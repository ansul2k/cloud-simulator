## UIC CS441 - Engineering Distributed Objects for Cloud Computing

## Project 1: Cloud Simulator

## Overview

In this homework, we aim to implement CloudSim Plus framework to create a Cloud Simulators for evaluating executions of applications in cloud datacenters with different characteristics and deployment models.

### What is CloudSim Plus?

CloudSim Plus is a modern, up-to-date, full-featured and fully documented simulation framework. It’s easy to use and extend, enabling modeling, simulation, and experimentation of Cloud computing infrastructures and application services. It allows developers to focus on specific system design issues to be investigated, without concerning the low-level details related to Cloud-based infrastructures and services.

## Instructions to run the Simulation

### Prerequisites

- Your workstation should have [SBT](https://www.scala-sbt.org/) installed.

### Steps to run the project via SBT shell

- Clone this private repository.
- Open the sbt shell and navigate to the path as the local repository's path.

#### Compile and Test
- Type the following command in the sbt shell in order to build the project and run unit tests.

```
sbt clean compile test
```

#### Run Simulation

- Type the following command in the sbt shell to run the simulation.

```
sbt run
```

### Main Components of the application

1. Broker:

    A Cloud Broker is an entity that manages the use, performance and delivery of cloud services, and negotiates relationships between cloud providers and cloud consumers. 

2. Virtual Machine

     A virtual machine (VM) is an emulation of a computer system. Virtual machines are based on computer architectures and provide functionality of a physical computer.

3.  Datacenter

    A data center is a physical facility that organizations use to house their critical applications and data. A data center's design is based on a network of computing and storage resources that enable the delivery of shared applications and data. The key components of a data center design include routers, switches, firewalls, storage systems, servers, and application-delivery controllers.

4.  Hosts

    A cloud host is a server that provides hosting services, frequently Web hosting, to customers via multiple connected servers that comprise a cloud.

5.  Processing Elements(PE)

    A processing element (PE) refers to a thread or process that is executing its own stream of instructions
    
6.  Load Balancing Mechanism: Vertical VM CPU Scaling

    Vertical scaling, also known as scale up and scale down, means increasing or decreasing virtual machine (VM) sizes in response to a workload.

7.  Vm Allocation Policies

    a. VM Allocation Policy Best Fit
    
        Allocates VM to the first suitable host from the list of hosts that has the most number of PEs in use.
    b. VM Allocation Policy Worst Fit
    
        Allocates VM to the first suitable host from the list of hosts that has the least number of PEs in use.
    c. VM Allocation Policy First Fit
    
        Allocates VM to the first host from the list of hosts.
    d. VM Allocation Policy Simple
    
        Allocates VM to the first suitable host from the list of hosts that has the least number of PEs in use.
    e. VM Allocation Policy Round Robin
    
        Allocates VM to the first host from the list of hosts.
        
8. Cloudlet Allocation:
    
    a.  Best Fit
    
        Allocates cloudlet to the first suitable vm from the list of vm that has the most number of PEs in use.
    b. Worst Fit
    
        Allocates cloudlet to the first suitable vm from the list of vm that has the least number of PEs in use.
    c. First Fit
    
        Allocates cloudlet to the first vm from the list of vm.

### Output
    
    Output with Allocation Policy as BestFit with Number of VM PEs = 1
    ![Alt text](images/BestFit.jpg)
    
    Output with Allocation Policy as WorstFit with Number of VM PEs = 1
    ![Alt text](images/WorstFit.jpg)
    
    Output with Allocation Policy as FirstFit with Number of VM PEs = 1
    ![Alt text](images/FirstFit.jpg)
    
    
    

### Analysis

    Number of VM PEs = 1:
    BestFit: The execution time is 392.91 s
    WorstFit: The execution time is 264.78 s
    FirstFit: The executing time is 274.69 s
    
    Number of VM PEs = 8:
    BestFit: The execution time is 51.83 s
    WorstFit: The execution time is 39.52 s
    FirstFit: The executing time is 40.79 s

### Improvements

