package org.cloudsimplus.builders.tables;

import org.cloudbus.cloudsim.cloudlets.Cloudlet;
import org.cloudbus.cloudsim.core.Identifiable;

import java.util.List;


public class CloudletTableBuilderSLA extends TableBuilderAbstract<Cloudlet>{

    private static final String TIME_FORMAT = "%.0f";
    private static final String SECONDS = "Seconds";
    private static final String CPU_CORES = "CPU cores";

    public CloudletTableBuilderSLA(final List<? extends Cloudlet> list, final Table table) {
        super(list, table);
    }

    public CloudletTableBuilderSLA(final List< ? extends Cloudlet> list){
        super(list);
    }

    @Override
    protected void createTableColumns() {
        final String ID = "ID";
        addColumnDataFunction(getTable().addColumn("Cloudlet", ID), Identifiable::getId);
        addColumnDataFunction(getTable().addColumn("Status "), cloudlet -> cloudlet.getStatus().name());

        TableColumn col = getTable().addColumn("StartTime", SECONDS).setFormat(TIME_FORMAT);
        addColumnDataFunction(col, Cloudlet::getExecStartTime);

        col = getTable().addColumn("FinishTime", SECONDS).setFormat(TIME_FORMAT);
        addColumnDataFunction(col, cl -> roundTime(cl, cl.getFinishTime()));

        col = getTable().addColumn("ExecTime", SECONDS).setFormat(TIME_FORMAT);
        addColumnDataFunction(col, cl -> roundTime(cl, cl.getActualCpuTime()));

        col = getTable().addColumn("Total Time", SECONDS).setFormat(TIME_FORMAT);
        addColumnDataFunction(col, cl-> roundTime(cl, cl.getFinishTime() - cl.getExecStartTime()));
    }

    private double roundTime(final Cloudlet cloudlet, final double time) {

        /*If the given time minus the start time is less than 1,
         * it means the execution time was less than 1 second.
         * This way, it can't be round.*/
        if(time - cloudlet.getExecStartTime() < 1){
            return time;
        }

        final double startFraction = cloudlet.getExecStartTime() - (int) cloudlet.getExecStartTime();
        return Math.round(time - startFraction);
    }
}
