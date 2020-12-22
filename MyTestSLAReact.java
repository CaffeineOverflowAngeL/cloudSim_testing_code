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
import java.util.HashMap;
import java.util.List;

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
public class MyTestSLAReact {



    private static final int HOSTS = 1;
    private static final int HOST_PES = 16;

    private static final int VMS = 4;
    private static final int VM_PES = 4;

    private static final int CLOUDLETS = 120;
    private static final int CLOUDLET_PES = 2;
    private static final int CLOUDLET_LENGTH = 10000;

    private final CloudSim simulation;
    private DatacenterBroker broker0;
    private List<Vm> vmList;
    private List<Cloudlet> cloudletList;
    private Datacenter datacenter0;


    public static void main(String[] args) {
        new MyTestSLAReact();
    }

    private MyTestSLAReact() {
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
        //simulation.addOnClockTickListener(this::slaReactiveMech);
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

        final ContinuousDistribution random = new NormalDistr( 10000, 500);
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
        final ContinuousDistribution random = new NormalDistr( 10000, 500);

        if((int)evt.getTime() == delay || (int)evt.getTime() == delay*2){
            List<Cloudlet> newCloudletList = new ArrayList<>(CLOUDLETS);
            System.out.printf("\n# Dynamically creating 2 Cloudlets at time %.2f\n", evt.getTime());
            Cloudlet cloudlet1 = new CloudletSimple((int) random.sample(), 2);

            newCloudletList.add(cloudlet1);
            Cloudlet cloudlet2 = new CloudletSimple((int) random.sample(), 2);
            newCloudletList.add(cloudlet2);
            broker0.submitCloudletList(newCloudletList);
        }
    }

    private void slaReactiveMech (EventInfo evt) {

        HashMap<String,Double> SLAMetrics = getSLAValues();
        if (true)
            /*

                HOUSTON WE HAVE A PROBLEM

             */

        if((int)evt.getTime() == 0){
            System.out.println(SLAMetrics.get("MAXTime"));
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

            double totalTime = cloudletList.get(pos).getFinishTime() - cloudletList.get(pos).getArrivalTime(datacenter0);

            //Increasing Task Completion Time Violations by one every time the total time of a cloudlet is greater
            // than TaskCompletion maximum value given in the SLA Contract
            if ( totalTime > MAXTime.getValue()){
                totalTaskCompletionTimeViolations ++;
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

    /*
    /getSLAValues function gets all the values from CustomerSLA.json
    /and stores them into a hash map so they can be accessed by the
    /main program
     */

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
}
