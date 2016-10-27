package template;

public class Solution {
	
	private double totalCost;
	
	private int[] weights;
	private AgentTask[] vehiclesFirstTask;
	
	public Solution(double totalCost, int[] weights, AgentTask[] vehiclesFirstTask) {
		this.totalCost = totalCost;
		this.weights = weights.clone();
		this.vehiclesFirstTask = vehiclesFirstTask.clone();
	}
	
	public double getTotalCost() {
		return totalCost;
	}
	
	public int[] getWeights() {
		return weights.clone();
	}
	
	public AgentTask[] getVehiclesFirstTask() {
		return vehiclesFirstTask.clone();
	}
	
}
