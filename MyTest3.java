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

import org.cloudbus.cloudsim.brokers.DatacenterBroker;
import org.cloudbus.cloudsim.brokers.DatacenterBrokerSimple;
import org.cloudbus.cloudsim.cloudlets.Cloudlet;
import org.cloudbus.cloudsim.cloudlets.CloudletSimple;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.datacenters.Datacenter;
import org.cloudbus.cloudsim.datacenters.DatacenterSimple;
import org.cloudbus.cloudsim.distributions.ContinuousDistribution;
import org.cloudbus.cloudsim.distributions.ExponentialDistr;
import org.cloudbus.cloudsim.distributions.NormalDistr;
import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.hosts.HostSimple;
import org.cloudbus.cloudsim.resources.Pe;
import org.cloudbus.cloudsim.resources.PeSimple;
import org.cloudbus.cloudsim.schedulers.cloudlet.CloudletSchedulerCompletelyFair;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModelDynamic;
import org.cloudbus.cloudsim.vms.Vm;
import org.cloudbus.cloudsim.vms.VmSimple;
import org.cloudsimplus.builders.tables.CloudletTableBuilderSLA;
import org.cloudsimplus.builders.tables.CloudletsTableBuilder;
import org.cloudsimplus.listeners.EventInfo;
import org.cloudsimplus.slametrics.SlaContract;
import org.cloudsimplus.slametrics.SlaMetric;
import org.cloudsimplus.slametrics.SlaMetricDimension;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * A minimal but organized, structured and re-usable CloudSim Plus example
 * which shows good coding practices for creating simulation scenarios.
 *
 * <p>It defines a set of constants that enables a developer
 * to change the number of Hosts, VMs and Cloudlets to create
 * and the number of {@link Pe}s for Hosts, VMs and Cloudlets.</p>
 *
 * @author Manoel Campos da Silva Filho
 * @since CloudSim Plus 1.0
 */
public class MyTest3 {



    private static final int HOSTS = 1;
    private static final int HOST_PES = 16;

    private static final int VMS = 4;
    private static final int VM_PES = 4;

    private static final int CLOUDLETS = 120;
    private static final int CLOUDLET_PES = 2;
    private static final int CLOUDLET_LENGTH = 100000;

    private final CloudSim simulation;
    private DatacenterBroker broker0;
    private List<Vm> vmList;
    private List<Cloudlet> cloudletList;
    private Datacenter datacenter0;

    public static void main(String[] args) {
        new MyTest3();
    }

    private MyTest3() {
        /*Enables just some level of log messages.
          Make sure to import org.cloudsimplus.util.Log;*/
        //Log.setLevel(ch.qos.logback.classic.Level.WARN);

        simulation = new CloudSim();
        datacenter0 = createDatacenter();

        //Creates a broker that is a software acting on behalf a cloud customer to manage his/her VMs and Cloudlets
        broker0 = new DatacenterBrokerSimple(simulation);

        vmList = createVms();
        cloudletList = createCloudlets();
        broker0.submitVmList(vmList);
        broker0.submitCloudletList(cloudletList);

        simulation.addOnClockTickListener(this::createDynamicCloudlet);
        simulation.addOnClockTickListener(this::ReactiveMech);
        //simulation.addOnClockTickListener(this::cancelCloudlets);
        simulation.start();


        final List<Cloudlet> finishedCloudlets = broker0.getCloudletFinishedList();
        new CloudletsTableBuilder(finishedCloudlets).build();
        new CloudletTableBuilderSLA(finishedCloudlets).build();

        // We run SLAViolations function after simulation completion so it does not affect the run times in the cpu.
        slaViolations(finishedCloudlets);


    }

    /**
     * Creates a Datacenter and its Hosts.
     */
    private Datacenter createDatacenter() {
        final List<Host> hostList = new ArrayList<>(HOSTS);
        for(int i = 0; i < HOSTS; i++) {
            Host host = createHost();
            hostList.add(host);
        }

        //Uses a VmAllocationPolicySimple by default to allocate VMs
        return new DatacenterSimple(simulation, hostList);
    }

    private Host createHost() {
        final List<Pe> peList = new ArrayList<>(HOST_PES);
        //List of Host's CPUs (Processing Elements, PEs)
        for (int i = 0; i < HOST_PES; i++) {
            //Uses a PeProvisionerSimple by default to provision PEs for VMs
            peList.add(new PeSimple(1000));
        }

        final long ram = 2048; //in Megabytes
        final long bw = 10000; //in Megabits/s
        final long storage = 1000000; //in Megabytes

        /*
        Uses ResourceProvisionerSimple by default for RAM and BW provisioning
        and VmSchedulerSpaceShared for VM scheduling.
        */
        return new HostSimple(ram, bw, storage, peList);
    }

    /**
     * Creates a list of VMs.
     */
    private List<Vm> createVms() {
        final List<Vm> list = new ArrayList<>(VMS);
        for (int i = 0; i < VMS; i++) {
            //Uses a CloudletSchedulerTimeShared by default to schedule Cloudlets
            final Vm vm = new VmSimple(1000, VM_PES);
            vm.setCloudletScheduler(new CloudletSchedulerCompletelyFair());
            vm.setRam(512).setBw(1000).setSize(10000);
            list.add(vm);
        }

        return list;
    }

    /**
     * Creates a list of Cloudlets.
     */
    private List<Cloudlet> createCloudlets() {

        final ContinuousDistribution random = new NormalDistr( 100000, 500);
        final List<Cloudlet> list = new ArrayList<>(CLOUDLETS);

        //UtilizationModel defining the Cloudlets use only 50% of any resource all the time
        final UtilizationModelDynamic utilizationModel = new UtilizationModelDynamic(0.5);

        for (int i = 0; i < CLOUDLETS; i++) {
            final Cloudlet cloudlet = new CloudletSimple(((int) random.sample()), CLOUDLET_PES, utilizationModel);
            cloudlet.setSizes(1024);
            list.add(cloudlet);
        }

        return list;
    }

    private void createDynamicCloudlet (EventInfo evt) {
        final int delay = 5;
        final ContinuousDistribution random = new NormalDistr( 100000, 500);


        if(true){
            List<Cloudlet> newCloudletList = new ArrayList<>(CLOUDLETS);
            System.out.printf("\n# Dynamically creating 2 Cloudlets at time %.2f\n", evt.getTime());
            Cloudlet cloudlet1 = new CloudletSimple((int) random.sample(), 4);

            newCloudletList.add(cloudlet1);
            Cloudlet cloudlet2 = new CloudletSimple((int) random.sample(), 4);
            newCloudletList.add(cloudlet2);
            broker0.submitCloudletList(newCloudletList);
        }
    }

    private void slaViolations (List<Cloudlet> Cloudlets) {

        //Initiating SLA Contract from CustomerSLA.json file
        String file = "CustomerSLA.json";
        SlaContract contract = SlaContract.getInstance(file);

        //TaskCompletionMetric Values
        final SlaMetric TaskCompletionMetric = contract.getTaskCompletionTimeMetric();
        final SlaMetricDimension MAXTime = TaskCompletionMetric.getMaxDimension();

        //Fault Tolerance Metric Values
        final SlaMetric FaultToleranceMetric = contract.getFaultToleranceLevel();
        final SlaMetricDimension MINFaultTol = contract.getFaultToleranceLevel().getMinDimension();

        //Initializing Cloudlet List
        List<Cloudlet> cloudletList = Cloudlets;

        //Initiating Violation values
        int totalTaskCompletionTimeViolations = 0;
        int totalFaultToleranceViolations = 0;

        //
        for( int pos=0; pos<cloudletList.size(); pos++){

            /* DEBUGGING LINES
            System.out.println(cloudletList.get(pos).getId() + " : ID");
            System.out.println(cloudletList.get(pos).getFinishTime() + " : Finshed Time");
            System.out.println(cloudletList.get(pos).getExecStartTime() + " : Exec Start Time");
            System.out.println(cloudletList.get(pos).getStatus() + " : Status");
            System.out.println();
            */


            double totalTime = cloudletList.get(pos).getFinishTime() - cloudletList.get(pos).getExecStartTime();

            //Increasing Task Completion Time Violations by one every time the total time of a cloudlet is greater
            // than TaskCompletion maximum value given in the SLA Contract
            if ( totalTime > MAXTime.getValue()){
                totalTaskCompletionTimeViolations ++;
            }

            //Increasing FaultToleranceViolations by one every time a cloudlet failed its process.

            //UPDATE THIS IS NOT AN ACCURATE METHOD TO CALCULATE Fault Tolerance Violations
            if ( cloudletList.get(pos).getStatus().equals("FAILED")) {
                totalFaultToleranceViolations ++;
            }



        }

        System.out.println("Total Sla Time Violations: " + totalTaskCompletionTimeViolations);
        //System.out.println("Total Sla Time Violations: " + totalFaultToleranceViolations);

        //Checking if fault Tolerance commitement of  Customers SLA Contract has been violated
        /*
        if (totalFaultToleranceViolations >= MINFaultTol.getValue()){
            System.out.println("Total fault tolerance violations in the simulation : " + totalFaultToleranceViolations);
            System.out.println("Fault Tolerance Level minimum valued aggreed on the SLA :" + MINFaultTol.getValue());
            System.out.println("Contract was violated!");
        }
        */


        //Debugging Line
        //System.out.print(MAXTime.getValue());
    }

    private void ReactiveMech (EventInfo evt) {

        // TO DO WITH ID
        int check_time = 5;

        // Runs every check_times assigned value seconds.
        if((evt.getTime()%check_time) == 0) {
            System.out.println("Executing task resubmission control..");
            List<Cloudlet> createdList = new ArrayList<>(CLOUDLETS);
            createdList = broker0.getCloudletCreatedList();

            List<Cloudlet> resubmitList = new ArrayList<>(CLOUDLETS);


            for (int i = 0; i < createdList.size(); i++) {
                if (createdList.get(i).getStatus().compareTo(Cloudlet.Status.FAILED) == 0 ||
                    createdList.get(i).getStatus().compareTo(Cloudlet.Status.FAILED_RESOURCE_UNAVAILABLE) == 0){
                        resubmitList.add(createdList.get(i));
                }
            }

            if(resubmitList.size()!=0) {
                System.out.println("Cloudlets are being resubmitted :" + resubmitList.toString());
                broker0.submitCloudletList(resubmitList);
            }
        }

    }

    private void cancelCloudlets( EventInfo evt){
        if (evt.getTime()%2==0){
            broker0.getCloudletSubmittedList().remove(0);
        }
    }
}
