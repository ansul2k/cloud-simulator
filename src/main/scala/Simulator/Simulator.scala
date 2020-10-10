package Simulator

import org.cloudbus.cloudsim.brokers.{DatacenterBroker, DatacenterBrokerBestFit, DatacenterBrokerFirstFit, DatacenterBrokerSimple}
import org.cloudbus.cloudsim.cloudlets.{Cloudlet, CloudletAbstract, CloudletSimple}
import org.cloudbus.cloudsim.core.CloudSim
import org.cloudbus.cloudsim.hosts.Host
import org.cloudbus.cloudsim.provisioners.{PeProvisionerSimple, ResourceProvisionerSimple}
import org.cloudbus.cloudsim.resources.{Pe, PeSimple, Processor}
import org.cloudbus.cloudsim.schedulers.cloudlet.CloudletSchedulerTimeShared
import org.cloudbus.cloudsim.schedulers.vm.VmSchedulerTimeShared
import org.cloudbus.cloudsim.utilizationmodels.{UtilizationModel, UtilizationModelDynamic, UtilizationModelFull}
import org.cloudbus.cloudsim.vms.{Vm, VmSimple}
import org.cloudsimplus.autoscaling.{VerticalVmScaling, VerticalVmScalingSimple}
import org.cloudsimplus.builders.tables.{CloudletsTableBuilder, TextTableColumn}
import org.cloudsimplus.listeners.EventInfo
import java.util
import java.util.{Comparator, List}
import java.util.Comparator.{comparingDouble, comparingLong}

import com.typesafe.config.{ConfigFactory, ConfigList, ConfigObject, ConfigValue}
import org.cloudbus.cloudsim.allocationpolicies.{VmAllocationPolicy, VmAllocationPolicyBestFit, VmAllocationPolicyFirstFit, VmAllocationPolicySimple, VmAllocationPolicyWorstFit}
import org.cloudbus.cloudsim.datacenters.network.NetworkDatacenter
import org.cloudbus.cloudsim.hosts.network.NetworkHost
import org.cloudbus.cloudsim.network.switches.{AggregateSwitch, EdgeSwitch, RootSwitch}
import org.slf4j.{Logger, LoggerFactory}

import scala.jdk.CollectionConverters.CollectionHasAsScala


/**
 * This implements CloudSim Plus framework to create a broker that decides which datacenter to use
 * based on cloudlets/tasks configuration. Each datacenter has its own configuration that makes it unique,
 * they have multiple hosts, and they act as a different mix of IAAS, SAAS, PAAS and FAAS. They have different
 * pricing criteria. This program also implements load balancing using vertical cpu scaling and uses valious
 * allocation techniques based on the value in config file. It also takes all its input from two typesafe config
 * files and uses slf4j for logging.
 *
 * @author Ansul Goenka
 * @since CloudSim Plus 1.2.0
 * @see VerticalVmRamScalingExample
 * @see VerticalVmCpuScalingDynamicThreshold
 */
object Simulator {

  private val parameters = ConfigFactory.load("parameter.conf")
  private val SCHEDULING_INTERVAL = parameters.getInt("SCHEDULING_INTERVAL")
  private val HOSTS = parameters.getInt("HOSTS")
  private val HOST_PES = parameters.getInt("HOST_PES")
  private val VMS = parameters.getInt("VMS")
  private val VM_PES = parameters.getInt("VM_PES")
  private val VM_RAM = parameters.getStringList("VM_RAM")
  private val VM_STORAGE = parameters.getStringList("VM_STORAGE")
  private val VM_BW = parameters.getInt("VM_BW")
  private val CLOUDLETS = parameters.getInt("CLOUDLETS")
  private val CLOUDLETS_INITIAL_LENGTH = parameters.getInt("CLOUDLETS_INITIAL_LENGTH")
  private val DATACENTERS = parameters.getInt("DATACENTERS")
  private val DC_STORAGE = parameters.getStringList("DC_STORAGE")
  private val DC_RAM = parameters.getStringList("DC_RAM")
  private val DC_BW = parameters.getStringList("DC_BW")
  private val COST_STORAGE = parameters.getDouble("COST_STORAGE")
  private val COST_RAM = parameters.getDouble("COST_RAM")
  private val COST_BW = parameters.getDouble("COST_BW")
  private val DC_OS = parameters.getStringList("DC_OS")
  private val COST_TIME = parameters.getDouble("COST_TIME")
  private val lowerCpuThreshold = parameters.getDouble("lowerCpuThreshold")
  private val upperCpuThreshold = parameters.getDouble("upperCpuThreshold")
  private val scalingFactor = parameters.getDouble("scalingFactor")
  private val delay = parameters.getDouble("delay")
  private val mipsCapacityPe = parameters.getDouble("mipsCapacityPe")
  private val mipsCapacityVm = parameters.getDouble("mipsCapacityVm")
  private val numberOfPes = parameters.getInt("numberOfPes")
  private var createsVms = 0
  private val numberOfAgSwitches = parameters.getInt("aggregateSwitches")
  private val numberOfEdgeSwitches = parameters.getInt("edgeSwitches")
  private val vmallocationPolicy = parameters.getInt("vmallocationPolicy")
  private val cloudallocationPolicy = parameters.getInt("cloudallocationPolicy")

  private val simulation: CloudSim = new CloudSim
  private val broker0: DatacenterBroker = new DatacenterBrokerSimple(simulation)
  private var hostList: util.ArrayList[Host] =_
  private val vmList: util.ArrayList[Vm] = new util.ArrayList[Vm](VMS)
  private val cloudletList: util.ArrayList[Cloudlet] = new util.ArrayList[Cloudlet](CLOUDLETS)
  private var datacenterList: util.ArrayList[NetworkDatacenter] =_
  private val cloudletConf: ConfigList = ConfigFactory.load("cloudlets.conf").getList("Cloudlets")
  private val logger: Logger = LoggerFactory.getLogger("Simulator")


  def main(args: Array[String]): Unit = {
    VerticalVmCpuScaling()
  }

  /**
   * Function that creates brokers, cloudlets, datacenters, does the simulation using vertical vm cpu scaling
   */

  private def VerticalVmCpuScaling() {
    createCloudletLists()
    hostList = new util.ArrayList[Host](HOSTS)
    simulation.addOnClockTickListener(this.onClockTickListener)
    datacenterList = createDatacenters()
    vmList.addAll(createListOfScalableVms(VMS))
    broker0.setVmMapper(cloudletVmMapper)
    broker0.submitVmList(vmList).submitCloudletList(cloudletList)
    simulation.start
    printSimulationResults()
  }

  /**
   * Handles VM and cloudlet mapping based on the configuration of Cloudlet and also handles cloud allocation policy in
   * this case. case 1: Best Fit, case 2: Worst Fit and case 3: First Fit
   *
   * @param cloudlet cloudlet needed to mapped
   * @return vm that cloud is mapped to
   */

  private def cloudletVmMapper(cloudlet: Cloudlet): Vm = {
    val execVms: List[Vm] = cloudlet.getBroker.getVmExecList
    val sortByFreePesNumber: Comparator[Vm]  = comparingLong(getExpectedNumberOfFreeVmPes)
    cloudallocationPolicy match {
      case 1 => execVms.sort(sortByFreePesNumber)
      case 2 => execVms.sort(sortByFreePesNumber.reversed())
      case 3 => execVms
    }
    execVms.stream.filter((vm: Vm) => getRAM(vm) >= cloudlet.getUtilizationModelRam.getUtilization).filter((vm: Vm) =>
      getStorage(vm) >= cloudlet.getFileSize).findFirst().get()
  }

  /**
   * fetches ram for a given vm
   *
   * @param vm vm whose ram we need to find
   * @return ram of that vm
   */

  private def getRAM(vm: Vm): Double = {
    logger.debug("Ram %d MB".format(vm.getRam.getAvailableResource))
    vm.getRam.getAvailableResource
  }

  /**
   * fetches storage for a given vm
   *
   * @param vm vm whose ram we need to find
   * @return storage of that vm
   */

  private def getStorage(vm: Vm): Double = {
    logger.debug("Storage %d MB".format(vm.getStorage.getAvailableResource))
    vm.getStorage.getAvailableResource
  }

  /**
   * fetches number of free pes for a given vm
   *
   * @param vm vm whose ram we need to find
   * @return number of free pes of that vm
   */

  private def getExpectedNumberOfFreeVmPes(vm: Vm) = {
    val totalPesForCloudletsOfVm = vm.getBroker.getCloudletCreatedList.stream.filter((c: Cloudlet) => c.getVm == vm)
      .mapToLong((c: Cloudlet) => c.getNumberOfPes).sum
    val numberOfVmFreePes = vm.getNumberOfPes - totalPesForCloudletsOfVm
    numberOfVmFreePes
  }

  /**
   * Shows updates every time the simulation clock advances.
   *
   * @param evt information about the event happened (that for this Listener is just the simulation time)
   */

  private def onClockTickListener(evt: EventInfo): Unit = {
    vmList.forEach((vm: Vm) => System.out.printf("\t\tTime %6.1f: Vm %d CPU Usage: %6.2f%% (%2d vCPUs. " +
      "Running Cloudlets: #%d). RAM usage: %.2f%% (%d MB)%n", evt.getTime, vm.getId, vm.getCpuPercentUtilization *
      100.0, vm.getNumberOfPes, vm.getCloudletScheduler.getCloudletExecList.size, vm.getRam.getPercentUtilization * 100,
      vm.getRam.getAllocatedResource))
  }

  /**
   *  Prints a table that contains list of cloudlets, thier status, host, datacenter and vm they were assigned to,
   *  its length, number of pen used, startTime, finishTime, ExecTime and cost of simulation.
   */

  private def printSimulationResults(): Unit = {
    val finishedCloudlets: util.List[Nothing] = broker0.getCloudletFinishedList
    val sortByVmId: Comparator[Cloudlet] = comparingDouble((c: Cloudlet) => c.getVm.getId)
    val sortByStartTime: Comparator[Cloudlet] = comparingDouble((c: Cloudlet) => c.getExecStartTime)
    finishedCloudlets.sort(sortByVmId.thenComparing(sortByStartTime))
    new CloudletsTableBuilder(finishedCloudlets).addColumn(new TextTableColumn("Storage Used","MB"),
      (cloudlet:Cloudlet) => cloudlet.getFileSize).addColumn(new TextTableColumn("RAM Used","MB"),
      (cloudlet:Cloudlet) => cloudlet.getUtilizationOfRam.asInstanceOf[Int]).addColumn(new TextTableColumn(
      "Total Cost", "Dollars"), (cloudlet:Cloudlet) => Math.round((cloudlet.getUtilizationOfRam * COST_RAM +
      cloudlet.getFileSize * COST_STORAGE)*(cloudlet.getFinishTime-cloudlet.getExecStartTime)*COST_TIME*100)/100.0).build()
  }

  /**
   * Creates Datacenter list and its Hosts.
   *
   * @return the list of datacenters
   * @see #createHost()
   */
   def createDatacenters(): util.ArrayList[NetworkDatacenter] = {
    logger.info("------------Creating Network Datacenters and Hosts--------------------")
    val list: util.ArrayList[NetworkDatacenter] = new util.ArrayList[NetworkDatacenter](DATACENTERS)
    (0 until DATACENTERS).foreach {
      i=>list.add(createDatacenter(i))
      logger.info("Network DataCenter %d Created".format(i+1))
    }
    list
  }

  /**
   * Creates Datacenters and its Hosts.
   *
   * @param index of datacenter to assign datacenter characteristics
   * @return datacenter
   * @see #createHost()
   */

  private def createDatacenter(index: Int): NetworkDatacenter = {
    val numberOfHosts = EdgeSwitch.PORTS * AggregateSwitch.PORTS * RootSwitch.PORTS
    hostList = new util.ArrayList[Host](HOSTS)
    (0 until  numberOfHosts).foreach{
      i => hostList.add(createHost(DC_RAM.get(index).toLong, DC_BW.get(index).toLong, DC_STORAGE.get(index).toLong))
        logger.info("Host %d created".format(i))
    }
    val dc : NetworkDatacenter = vmallocationPolicy match {
      case 1 => new NetworkDatacenter(simulation, hostList, new VmAllocationPolicyBestFit())
      case 2 => new NetworkDatacenter(simulation, hostList, new VmAllocationPolicyWorstFit())
      case 3 => new NetworkDatacenter(simulation, hostList, new VmAllocationPolicyFirstFit())
    }

    dc.setSchedulingInterval(SCHEDULING_INTERVAL).getCharacteristics.setCostPerBw(COST_BW)
      .setCostPerMem(COST_RAM).setCostPerStorage(COST_STORAGE).setCostPerSecond(COST_TIME)
      .setOs(DC_OS.get(index))
    createNetwork(dc)
    dc
  }

  def createHost(ram: Long, bw: Long, storage: Long): Host = {
    val peList: util.ArrayList[Pe] = new util.ArrayList[Pe](HOST_PES)
    (0 until  HOST_PES).map {
      _ => peList.add(new PeSimple(mipsCapacityPe, new PeProvisionerSimple))
    }
    new NetworkHost(ram, bw, storage, peList).setRamProvisioner(new ResourceProvisionerSimple()).
      setBwProvisioner(new ResourceProvisionerSimple).setVmScheduler(new VmSchedulerTimeShared)
  }

  /**
   * Creates Network by implementing rack/cluster organisation using root, aggregate and edge switches.
   *
   * @param dc : NetworkDatacenter Object
   * @see #getSwitchIndex()
   */

  private def createNetwork(dc : NetworkDatacenter): Unit ={
    val rootSwitch:RootSwitch = new RootSwitch(simulation, dc)

    val aggregateSwitches = (0 until numberOfAgSwitches).map {
      _ => val aggregateSwitch = new AggregateSwitch(simulation, dc)
        dc.addSwitch(aggregateSwitch)
        aggregateSwitch.getUplinkSwitches.add(rootSwitch)
        rootSwitch.getDownlinkSwitches.add(aggregateSwitch)
        aggregateSwitch
    }
    val edgeSwitches = (0 until numberOfEdgeSwitches).map {
      i => val edgeSwitch = new EdgeSwitch(simulation, dc)
        dc.addSwitch(edgeSwitch)
        val switchNumber = i / AggregateSwitch.PORTS
        edgeSwitch.getUplinkSwitches.add(aggregateSwitches(switchNumber))
        aggregateSwitches(switchNumber).getDownlinkSwitches.add(edgeSwitch)
        edgeSwitch
    }
    dc.getHostList[NetworkHost].asScala.foreach { host =>
      val switchNum = getSwitchIndex(host, EdgeSwitch.PORTS)
      edgeSwitches(switchNum).connectHost(host)
    }
  }

  /**
   * Calculates the value of switchNum
   *
   * @param host : NetworkHost
   * @param switchPorts: Number of Ports in Edge Switch
   */

  def getSwitchIndex(host: NetworkHost, switchPorts: Int): Int = Math.round(host.getId % Integer.MAX_VALUE / switchPorts)

  /**
   * Creates a list of initial VMs in which each VM is able to scale vertically
   * when it is over or underloaded.
   *
   * @param numberOfVms number of VMs to create
   * @return the list of scalable VMs
   * @see #createVerticalPeScaling()
   */
  private def createListOfScalableVms(numberOfVms: Int) = {
    val newList = new util.ArrayList[Vm](numberOfVms)
    logger.info("-------------------------Creating Vms---------------------------------")
    (0 until  numberOfVms).foreach {
      i => newList.add(createVm(i).setPeVerticalScaling(createVerticalPeScaling))
      logger.info("VM %d created".format(i))
    }
    newList
  }

  /**
   * Creates a Vm object.
   *
   * @return the created Vm
   */
  def createVm(index : Int): Vm = {
    val id: Int = {
      createsVms += 1; createsVms - 1
    }
    new VmSimple(id, mipsCapacityVm, VM_PES).setRam(VM_RAM.get(index).toInt).setBw(VM_BW).setSize(VM_STORAGE.get(index).
      toInt).setCloudletScheduler(new CloudletSchedulerTimeShared)
  }

  /**
   * Creates a  VerticalVmScaling for scaling VM's CPU when it's under or overloaded.
   *
   * Realize the lower and upper thresholds are defined inside this method by using
   * references to the methods @see #lowerCpuUtilizationThreshold ( Vm )
   * and @see #upperCpuUtilizationThreshold ( Vm ).
   * These methods enable defining thresholds in a dynamic way
   * and even different thresholds for distinct VMs.
   *
   *
   * @see #createListOfScalableVms(int)
   */
  def createVerticalPeScaling: VerticalVmScaling = { //The percentage in which the number of PEs has to be scaled
    //verticalCpuScaling.setResourceScaling(new ResourceScalingInstantaneous());
    new VerticalVmScalingSimple(classOf[Processor], scalingFactor).setResourceScaling((vs: VerticalVmScaling) =>
      2 * vs.getScalingFactor * vs.getAllocatedResource).setLowerThresholdFunction(this.lowerCpuUtilizationThreshold).
      setUpperThresholdFunction(this.upperCpuUtilizationThreshold)
  }

  /**
   * Defines the minimum CPU utilization percentage that indicates a Vm is underloaded.
   * This function is using a statically defined threshold, but it would be defined
   * a dynamic threshold based on any condition you want.
   * A reference to this method is assigned to each Vertical VM Scaling created.
   *
   * @param vm the VM to check if its CPU is underloaded.
   *           <b>The parameter is not being used internally, which means the same
   *           threshold is used for any Vm.</b>
   * @return the lower CPU utilization threshold
   * @see #createVerticalPeScaling()
   */
  private def lowerCpuUtilizationThreshold(vm: Vm): Double = {
    lowerCpuThreshold
  }

  /**
   * Defines the maximum CPU utilization percentage that indicates a Vm is overloaded.
   * This function is using a statically defined threshold, but it would be defined
   * a dynamic threshold based on any condition you want.
   * A reference to this method is assigned to each Vertical VM Scaling created.
   *
   * @param vm the VM to check if its CPU is overloaded.
   *           The parameter is not being used internally, that means the same
   *           threshold is used for any Vm.
   * @return the upper CPU utilization threshold
   * @see #createVerticalPeScaling()
   */
  private def upperCpuUtilizationThreshold(vm: Vm): Double = {
    upperCpuThreshold
  }

  /**
   * Creates lists of Cloudlets to be submitted to the broker with different delays,
   * simulating their arrivals at different times.
   * Adds all created Cloudlets to the cloudletList
   */

   private def createCloudletLists(): Unit = {
    //Creates a List of Cloudlets that will start running immediately when the simulation starts
    logger.info("-----------------------Creating Cloudlets-----------------------------")
    (0 until cloudletConf.size()).foreach {
      i => cloudletList.add(createCloudlet(CLOUDLETS_INITIAL_LENGTH + (i * 1000), numberOfPes, cloudletConf.get(i)))
      logger.info("Cloudlet with FileSize: %d MB OutputSize %d MB RAM %s MB created".format(cloudletList.get(i).getFileSize,
        cloudletList.get(i).getOutputSize, cloudletList.get(i).getUtilizationModelRam.getUtilization))
    }
  }

  /**
   * Creates a single Cloudlet with no delay, which means the Cloudlet arrival time will
   * be zero (exactly when the simulation starts).
   *
   * @param length      the Cloudlet length
   * @param numberOfPes the number of PEs the Cloudlet requires
   * @param cloudletVal configuration of a Cloudlet
   * @return the created Cloudlet
   */
  def createCloudlet(length: Long, numberOfPes: Int, cloudletVal: ConfigValue): Cloudlet = {
    createCloudlet(length, numberOfPes, delay, cloudletVal)
  }

  /**
   * Creates a single Cloudlet and assigns features to it.
   *
   * @param length      the length of the cloudlet to create.
   * @param numberOfPes the number of PEs the Cloudlets requires.
   * @param delay       the delay that defines the arrival time of the Cloudlet at the Cloud infrastructure.
   * @param cloudletVal configuration of a Cloudlet
   * @return the created Cloudlet
   */
  private def createCloudlet(length: Long, numberOfPes: Int, delay: Double, cloudletVal: ConfigValue): Cloudlet = {
    val cl: Cloudlet = new CloudletSimple(length, numberOfPes)
    val clObject = cloudletVal.asInstanceOf[ConfigObject].toConfig
    cl.setFileSize(clObject.getLong("CL_FileSize")).setOutputSize(clObject.getLong("CL_OutputSize")
      ).setUtilizationModelCpu(new UtilizationModelFull).setUtilizationModelRam(new UtilizationModelDynamic(
      UtilizationModel.Unit.ABSOLUTE, clObject.getInt("CL_RAM"))).setUtilizationModelBw(
      new UtilizationModelFull).setSubmissionDelay(delay)
    cl.setId(clObject.getLong("CL_ID"))
    cl
  }
}
