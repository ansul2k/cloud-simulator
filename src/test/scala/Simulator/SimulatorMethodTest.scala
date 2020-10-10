package Simulator

import org.cloudbus.cloudsim.datacenters.Datacenter
import java.util

import com.typesafe.config.{ConfigFactory, ConfigList}
import junit.framework.TestCase
import org.cloudbus.cloudsim.cloudlets.Cloudlet
import org.cloudbus.cloudsim.hosts.HostSimple
import org.cloudbus.cloudsim.vms.Vm
import org.cloudsimplus.autoscaling.VerticalVmScaling

class SimulatorMethodTest extends TestCase{

  def testCreateDataCenterList {
    val returnVal = Simulator.createDatacenters()
    assert(returnVal.isInstanceOf[util.ArrayList[Datacenter]])
  }

  def testCreateVm {
    val returnVal = Simulator.createVm(0)
    assert(returnVal.isInstanceOf[Vm])
  }

  def testCreateHost {
    val returnVal = Simulator.createHost(10000, 1000, 10000)
    assert(returnVal.isInstanceOf[HostSimple])
  }

  val cloudlet: ConfigList = ConfigFactory.load("cloudlets.conf").getList("Cloudlets")

  def testCreateCloudlets {
    val returnVal = Simulator.createCloudlet(10000, 2, cloudlet.get(0))
    assert(returnVal.isInstanceOf[Cloudlet])
  }

  def testCreateVerticalPeScaling{
    val returnval = Simulator.createVerticalPeScaling
    assert(returnval.isInstanceOf[VerticalVmScaling])
  }
}
