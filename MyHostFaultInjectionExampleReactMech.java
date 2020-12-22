/*
 * CloudSim Plus: A modern, highly-extensible and easier-to-use Framework for
 * Modeling and Simulation of Cloud Computing Infrastructures and Services.
 * http://cloudsimplus.org
 *
 *     Copyright (C) 2015-2018 Universidade da Beira Interior (UBI, Portugal) and
 *     the Instituto Federal de Educação Ciência e Tecnologia do Tocantins (IFTO, Brazil).
 *
 *     This file is part of CloudSim Plus.
 *
 *     CloudSim Plus is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     CloudSim Plus is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with CloudSim Plus. If not, see <http://www.gnu.org/licenses/>.
 */
package org.cloudsimplus.examples;

import org.cloudbus.cloudsim.allocationpolicies.VmAllocationPolicySimple;
import org.cloudbus.cloudsim.brokers.DatacenterBroker;
import org.cloudbus.cloudsim.brokers.DatacenterBrokerSimple;
import org.cloudbus.cloudsim.cloudlets.Cloudlet;
import org.cloudbus.cloudsim.cloudlets.CloudletSimple;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.datacenters.Datacenter;
import org.cloudbus.cloudsim.datacenters.DatacenterSimple;
import org.cloudbus.cloudsim.distributions.PoissonDistr;
import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.hosts.HostSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.ResourceProvisioner;
import org.cloudbus.cloudsim.provisioners.ResourceProvisionerSimple;
import org.cloudbus.cloudsim.resources.Pe;
import org.cloudbus.cloudsim.resources.PeSimple;
import org.cloudbus.cloudsim.schedulers.cloudlet.CloudletSchedulerTimeShared;
import org.cloudbus.cloudsim.schedulers.vm.VmScheduler;
import org.cloudbus.cloudsim.schedulers.vm.VmSchedulerTimeShared;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModel;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModelDynamic;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModelFull;
import org.cloudbus.cloudsim.vms.Vm;
import org.cloudbus.cloudsim.vms.VmSimple;
import org.cloudsimplus.builders.tables.CloudletsTableBuilder;
import org.cloudsimplus.faultinjection.HostFaultInjection;
import org.cloudsimplus.faultinjection.VmClonerSimple;
import org.cloudsimplus.listeners.EventInfo;
import org.cloudsimplus.slametrics.SlaContract;
import org.cloudsimplus.slametrics.SlaMetric;
import org.cloudsimplus.slametrics.SlaMetricDimension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Example showing how to inject random {@link Pe} faults into Hosts using
 * {@link HostFaultInjection} objects.
 *
 * @author raysaoliveira
 * @since CloudSim Plus 1.2.0
 */
public final class MyHostFaultInjectionExampleReactMech {
    private static final int SCHEDULE_TIME_TO_PROCESS_DATACENTER_EVENTS = 0;

    private static final int HOST_MIPS_BY_PE = 1000;
    private static final int HOST_PES = 4;
    private static final long HOST_RAM = 500000; //host memory (Megabyte)
    private static final long HOST_STORAGE = 1000000; //host storage
    private static final long HOST_BW = 100000000L;

    /*The average number of failures expected to happen each hour
    in a Poisson Process, which is also called event rate or rate parameter.*/
    private static final double MEAN_FAILURE_NUMBER_PER_HOUR = 0.01;

    private List<Host> hostList;

    private static final int VM_MIPS = 1000;
    private static final long VM_SIZE = 1000; //image size (Megabyte)
    private static final int VM_RAM = 10000; //vm memory (Megabyte)
    private static final long VM_BW = 100000;
    private static final int VM_PES = 2; //number of cpus

    private static final int CLOUDLET_PES = 2;
    private static final long CLOUDLET_LENGHT = 2800_000_000L;
    private static final long CLOUDLET_FILESIZE = 300;
    private static final long CLOUDLET_OUTPUTSIZE = 300;

    /**
     * Number of Hosts to create for each Datacenter. The number of elements in
     * this array defines the number of Datacenters to be created.
     */
    private static final int HOSTS = 10;
    private static final int VMS = 2;

    private static final int CLOUDLETS = 6;

    private final List<Vm> vmList = new ArrayList<>(VMS);
    private final List<Cloudlet> cloudletList = new ArrayList<>(CLOUDLETS);
    private CloudSim simulation;
    private final DatacenterBroker broker;
    private Datacenter datacenter;

    private HostFaultInjection fault;

    private long hostFaults = 0;

    /**
     * The Poisson Random Number Generator used to generate failure times (in hours).
     */
    private PoissonDistr poisson;

    /**
     * Starts the example.
     *
     * @param args
     */
    public static void main(String[] args) {
        new MyHostFaultInjectionExampleReactMech();
    }

    private MyHostFaultInjectionExampleReactMech() {
        /*Enables just some level of log messages.
          Make sure to import org.cloudsimplus.util.Log;*/
        //Log.setLevel(ch.qos.logback.classic.Level.WARN);

        System.out.println("Starting " + getClass().getSimpleName());
        simulation = new CloudSim();

        datacenter = createDatacenter();

        broker = new DatacenterBrokerSimple(simulation);
        createAndSubmitVms();
        createAndSubmitCloudlets();
        createFaultInjectionForHosts(datacenter);

        simulation.addOnClockTickListener(this::slaReactiveMech);
        simulation.start();
        new CloudletsTableBuilder(broker.getCloudletFinishedList()).build();
        //slaViolations(broker.getCloudletFinishedList());

        System.out.printf(
            "%n# Mean Number of Failures per Hour: %.3f (1 failure expected at each %.2f hours).%n",
            MEAN_FAILURE_NUMBER_PER_HOUR, poisson.getInterArrivalMeanTime());
        System.out.printf("# Number of Host faults: %d%n", fault.getNumberOfHostFaults());
        System.out.printf("# Number of VM faults (VMs destroyed): %d%n", fault.getNumberOfFaults());
        System.out.printf("# Time the simulations finished: %.4f hours%n", simulation.clockInHours());
        System.out.printf("# Mean Time To Repair Failures of VMs in minutes (MTTR): %.2f minute%n", fault.meanTimeToRepairVmFaultsInMinutes());
        System.out.printf("# Mean Time Between Failures (MTBF) affecting all VMs in minutes: %.2f minutes%n", fault.meanTimeBetweenVmFaultsInMinutes());
        System.out.printf("# Hosts MTBF: %.2f minutes%n", fault.meanTimeBetweenHostFaultsInMinutes());
        System.out.printf("# Availability: %.2f%%%n%n", fault.availability()*100);

        System.out.println(getClass().getSimpleName() + " finished!");

        //SLA
        slaViolations(broker.getCloudletFinishedList(),fault);


        //System.out.println(fault.getNumberOfFaults(broker));
    }

    public void createAndSubmitVms() {
        for (int i = 0; i < VMS; i++) {
            Vm vm = createVm();
            vmList.add(vm);
        }
        broker.submitVmList(vmList);
    }

    public Vm createVm() {
        Vm vm = new VmSimple(vmList.size()+1, VM_MIPS, VM_PES);
        vm
            .setRam(VM_RAM).setBw(VM_BW).setSize(VM_SIZE)
            .setCloudletScheduler(new CloudletSchedulerTimeShared());
        return vm;
    }

    /**
     * Creates the number of Cloudlets defined in {@link #CLOUDLETS} and submits
     * them to the created broker.
     */
    public void createAndSubmitCloudlets() {
        UtilizationModel utilizationModelDynamic = new UtilizationModelDynamic(0.1);
        UtilizationModel utilizationModelFull = new UtilizationModelFull();
        for (int i = 0; i < CLOUDLETS; i++) {
            Cloudlet c
                = new CloudletSimple(cloudletList.size()+1, CLOUDLET_LENGHT, CLOUDLET_PES)
                        .setFileSize(CLOUDLET_FILESIZE)
                        .setOutputSize(CLOUDLET_OUTPUTSIZE)
                        .setUtilizationModelCpu(utilizationModelFull)
                        .setUtilizationModelBw(utilizationModelDynamic)
                        .setUtilizationModelBw(utilizationModelDynamic);
            cloudletList.add(c);
        }

        broker.submitCloudletList(cloudletList);
    }

    private Datacenter createDatacenter() {
        hostList = new ArrayList<>();
        for (int i = 0; i < HOSTS; i++) {
            hostList.add(createHost());
        }
        System.out.println();

        Datacenter dc = new DatacenterSimple(simulation, hostList, new VmAllocationPolicySimple());
        dc.setSchedulingInterval(SCHEDULE_TIME_TO_PROCESS_DATACENTER_EVENTS);
        return dc;
    }

    /**
     * Creates a Host.
     *
     * @return
     */
    public Host createHost() {
        final List<Pe> pesList = createPeList(HOST_PES, HOST_MIPS_BY_PE);
        final ResourceProvisioner ramProvisioner = new ResourceProvisionerSimple();
        final ResourceProvisioner bwProvisioner = new ResourceProvisionerSimple();
        final VmScheduler vmScheduler = new VmSchedulerTimeShared();
        return new HostSimple(HOST_RAM, HOST_BW, HOST_STORAGE, pesList)
                .setRamProvisioner(ramProvisioner)
                .setBwProvisioner(bwProvisioner)
                .setVmScheduler(vmScheduler);
    }

    public List<Pe> createPeList(final int numberOfPEs, final long mips) {
        List<Pe> list = new ArrayList<>(numberOfPEs);
        for (int i = 0; i < numberOfPEs; i++) {
            list.add(new PeSimple(mips, new PeProvisionerSimple()));
        }

        return list;
    }

    /**
     * Creates the fault injection for host
     *
     * @param datacenter
     */
    private void createFaultInjectionForHosts(Datacenter datacenter) {
        //Use the system time to get random results every time you run the simulation
        //final long seed = System.currentTimeMillis();
        final long seed = 112717613L;
        this.poisson = new PoissonDistr(MEAN_FAILURE_NUMBER_PER_HOUR, seed);

        fault = new HostFaultInjection(datacenter, poisson);
        fault.setMaxTimeToFailInHours(800);

        fault.addVmCloner(broker, new VmClonerSimple(this::cloneVm, this::cloneCloudlets));
    }

    /**
     * Clones a VM by creating another one with the same configurations of a
     * given VM.
     *
     * @param vm the VM to be cloned
     * @return the cloned (new) VM.
     *
     * @see #createFaultInjectionForHosts(Datacenter)
     */
    private Vm cloneVm(Vm vm) {
        Vm clone = new VmSimple(vm.getMips(), (int) vm.getNumberOfPes());
        /*It' not required to set an ID for the clone.
        It is being set here just to make it easy to
        relate the ID of the vm to its clone,
        since the clone ID will be 10 times the id of its
        source VM.*/
        clone.setId(vm.getId() * 10);
        clone.setDescription("Clone of VM " + vm.getId());
        clone
            .setSize(vm.getStorage().getCapacity())
            .setBw(vm.getBw().getCapacity())
            .setRam(vm.getBw().getCapacity())
            .setCloudletScheduler(new CloudletSchedulerTimeShared());
        System.out.printf("%n%n# Cloning %s - MIPS %.2f Number of Pes: %d%n", vm, clone.getMips(), clone.getNumberOfPes());

        return clone;
    }

    /**
     * Clones each Cloudlet associated to a given VM. The method is called when
     * a VM is destroyed due to a Host failure and a snapshot from that VM (a
     * clone) is started into another Host. In this case, all the Cloudlets
     * which were running inside the destroyed VM will be recreated from scratch
     * into the VM clone, re-starting their execution from the beginning.
     *
     * @param sourceVm the VM to clone its Cloudlets
     * @return the List of cloned Cloudlets.
     * @see
     * #createFaultInjectionForHosts(Datacenter)
     */
    private List<Cloudlet> cloneCloudlets(Vm sourceVm) {
        final List<Cloudlet> sourceVmCloudlets = sourceVm.getCloudletScheduler().getCloudletList();
        final List<Cloudlet> clonedCloudlets = new ArrayList<>(sourceVmCloudlets.size());
        for (Cloudlet cl : sourceVmCloudlets) {
            Cloudlet clone = cloneCloudlet(cl);
            clonedCloudlets.add(clone);
            System.out.printf("# Created Cloudlet Clone for %s (Cloned Cloudlet Id: %d)%n", sourceVm, clone.getId());
        }

        return clonedCloudlets;
    }

    /**
     * Creates a clone from a given Cloudlet.
     *
     * @param source the Cloudlet to be cloned.
     * @return the cloned (new) cloudlet
     */
    private Cloudlet cloneCloudlet(Cloudlet source) {
        Cloudlet clone = new CloudletSimple(source.getLength(), source.getNumberOfPes());
        /*It' not required to set an ID for the clone.
        It is being set here just to make it easy to
        relate the ID of the cloudlet to its clone,
        since the clone ID will be 10 times the id of its
        source cloudlet.*/
        clone.setId(source.getId() * 10);
        clone
            .setUtilizationModelBw(source.getUtilizationModelBw())
            .setUtilizationModelCpu(source.getUtilizationModelCpu())
            .setUtilizationModelRam(source.getUtilizationModelRam());
        return clone;
    }

    private void slaViolations (List<Cloudlet> Cloudlets, HostFaultInjection fault) {

        //Initiating SLA Contract from CustomerSLA.json file
        String file = "CustomerSLA.json";
        SlaContract contract = SlaContract.getInstance(file);

        //TaskCompletionMetric Values
        final SlaMetric TaskCompletionMetric = contract.getTaskCompletionTimeMetric();
        final SlaMetricDimension MAXTime = TaskCompletionMetric.getMaxDimension();

        //Fault Tolerance Metric Values
        final SlaMetric FaultToleranceMetric = contract.getFaultToleranceLevel();
        final SlaMetricDimension MINFaultTol = contract.getFaultToleranceLevel().getMinDimension();

        //Availability
        final SlaMetric Availability = contract.getAvailabilityMetric();
        final SlaMetricDimension MINAvailability = contract.getAvailabilityMetric().getMinDimension();

        //Initializing Cloudlet List
        List<Cloudlet> cloudletList = Cloudlets;

        //Initiating Violation values
        boolean contractViolation = false;


        //Checking if fault Tolerance commitement of  Customers SLA Contract has been violated
        System.out.println("Contract agreed value of fault tolerance level minimum value is " + MINFaultTol.getValue());
        System.out.println("Number of Faults during simulations execution : " + fault.getNumberOfFaults());
        if (fault.getNumberOfFaults() >= MINFaultTol.getValue()){
            contractViolation=true;
        }

        System.out.println("Contract agreed value of availability level minimum value is " + MINAvailability.getValue() + "%");
        System.out.println("Availability during simulations execution : " + fault.availability()*100 +"%");
        if (fault.availability()*100 < MINAvailability.getValue()){
            contractViolation=true;
        }

        //Debugging Line
        //System.out.print(MAXTime.getValue());
    }

    public HashMap<String, Double> getSLAValues(){


        //Initiating SLA Contract from CustomerSLA.json file
        String file = "CustomerSLA.json";
        SlaContract contract = SlaContract.getInstance(file);

        //TaskCompletionMetric Values
        final SlaMetric TaskCompletionMetric = contract.getTaskCompletionTimeMetric();
        final SlaMetricDimension MAXTime = TaskCompletionMetric.getMaxDimension();

        //Fault Tolerance Metric Values
        final SlaMetric FaultToleranceMetric = contract.getFaultToleranceLevel();
        final SlaMetricDimension MINFaultTol = contract.getFaultToleranceLevel().getMinDimension();

        //Availability Metric Values
        final SlaMetric AvailabilityMetric = contract.getAvailabilityMetric();
        final SlaMetricDimension MINAvailability = contract.getAvailabilityMetric().getMinDimension();

        //CPU Utilization Metric Values
        final SlaMetric CpuUtilMetric = contract.getCpuUtilizationMetric();
        final SlaMetricDimension MAXCpuUtil = contract.getCpuUtilizationMetric().getMaxDimension();

        HashMap<String,Double> SLAMetrics = new HashMap<String,Double>();
        SLAMetrics.put("MAXTime", MAXTime.getValue());
        SLAMetrics.put("MINFaultTol", MINFaultTol.getValue());
        SLAMetrics.put("MINAvailability",MINAvailability.getValue());
        SLAMetrics.put("MAXCpuUtil",MAXCpuUtil.getValue());

        return SLAMetrics;


    }

    private void slaReactiveMech (EventInfo evt) {
        List<Vm> vmList2 = new ArrayList<>(VMS);
         List<Cloudlet> cloudletListRE = new ArrayList<>(CLOUDLETS);
        HashMap<String,Double> SLAMetrics = getSLAValues();
        long NumberOfFaults =0 ;


        //Provisioning

        if (fault.availability()*100<= SLAMetrics.get("MINAvailability")){
            System.out.println("SYSTEM DOWN , SEND HELP");
            System.out.println("Availability SLA Condition has been violated!  Current Availability: " + fault.availability());
        }

        if (fault.getNumberOfFaults() > NumberOfFaults){
            System.out.println("SYSTEM DOWN , SEND HELP");
            System.out.println("Before: " + NumberOfFaults); //Debugging
            NumberOfFaults = fault.getNumberOfFaults();
            System.out.println("After: " + NumberOfFaults); //Debugging
        }

        if (fault.getNumberOfHostFaults() > this.hostFaults){
            System.out.println("SYSTEM DOWN , SEND HELP");
            System.out.println("Before: " + this.hostFaults); //Debugging
            this.hostFaults = fault.getNumberOfHostFaults();
            System.out.println("After: " + this.hostFaults); //Debugging

            /*
            if(datacenter.getActiveHostsNumber()<3) {
                Host host = createHost();
                datacenter.addHost(host);
                System.out.printf("%n %.2f: # Physically expanding the %s by adding the new %s to it.", evt.getTime(), datacenter, host);


            }
            */

            System.out.println(broker.getVmCreatedList());
            System.out.println(broker.getCloudletSubmittedList());
            System.out.println(broker.getCloudletWaitingList());
            System.out.println(broker.getVmFailedList());



        }
//            if((int)evt.getTime() == 0){
//                System.out.println(SLAMetrics.get("MAXTime"));
//            }
    }
}
