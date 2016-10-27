package template;

import org.omg.CORBA.VersionSpecHelper;

public class Solution {
	
	private double totalDistance;
	
	private double[] weights;
	private int[] vehiclesFirstTask;
	private int[] taskActions;
	
	public Solution(double totalDistance, double[] weights, int[] vehiclesFirstTask, int[] taskActions) {
		this.totalDistance = totalDistance;
		this.weights = weights.clone();
		this.vehiclesFirstTask = vehiclesFirstTask.clone();
		this.taskActions = taskActions.clone();
	}
	
	public double getTotalDistance() {
		return totalDistance;
	}
	
	public double[] getWeights() {
		return weights.clone();
	}
	
	public int[] getVehiclesFirstTask() {
		return vehiclesFirstTask.clone();
	}
	
	public int[] getTaskActions() {
		return taskActions.clone();
	}

}
