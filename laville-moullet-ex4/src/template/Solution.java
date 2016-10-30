package template;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import logist.simulation.Vehicle;
import logist.task.Task;
import logist.topology.Topology.City;

public class Solution {
	
	private double totalCost;
	
	private int[] weights;
	private AgentTask[] vehiclesFirstTask;
	private List<Vehicle> vehicles;
	
	public Solution(double totalCost, int[] weights, AgentTask[] vehiclesFirstTask, List<Vehicle> vehicles) {
		this.totalCost = totalCost;
		this.weights = weights.clone();
		this.vehiclesFirstTask = vehiclesFirstTask.clone();
		this.vehicles = new ArrayList<Vehicle>(vehicles);
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
	
	public List<Vehicle> getVehicles() {
		return this.vehicles;
	}
	
	public void setVehiclesFirstTask(int i, AgentTask agentTask) {
		this.vehiclesFirstTask[i] = agentTask;
	}
	
	public Solution clone() {
		AgentTask[] vehiclesFirstTask = new AgentTask[this.vehiclesFirstTask.length];
		
		for (int vehiclesIdx = 0; vehiclesIdx < this.weights.length; vehiclesIdx++) {
			AgentTask currentTask = this.vehiclesFirstTask[vehiclesIdx];
			AgentTask lastTask = null;
			
			while (currentTask != null) {
				AgentTask newTask = new AgentTask(currentTask.getTask(), currentTask.isPickup());
				
				if (lastTask == null) {
					vehiclesFirstTask[vehiclesIdx] = newTask;
				}
				else {
					lastTask.setNext(newTask);
				}
				
				lastTask = newTask;
				currentTask = currentTask.getNext();
			}
		}
		
		return new Solution(totalCost, weights, vehiclesFirstTask, this.vehicles);
	}
	
	public boolean checkCorrectSolution() {
		for (int vehiclesIdx = 0; vehiclesIdx < this.weights.length; vehiclesIdx++) {
			AgentTask currentTask = this.vehiclesFirstTask[vehiclesIdx];
			Set<Task> toDeliver = new HashSet<Task>();
			int currentWeight = 0;
			while (currentTask != null) {
				if (currentTask.isPickup()) {
					boolean added = toDeliver.add(currentTask.getTask());
					if (!added) {
						return false;
					}
				}
				else {
					boolean removed = toDeliver.remove(currentTask.getTask());
					if (!removed) {
						return false;
					}
				}
				if (currentTask.isPickup()) {
					currentWeight += currentTask.getTask().weight;
				}
				else {
					currentWeight -= currentTask.getTask().weight;
				}
				if (currentWeight > this.vehicles.get(vehiclesIdx).capacity()) {
					return false;
				}
				
				currentTask = currentTask.getNext();
			}
		}
		return true;
	}

	public AgentTask getAgentTaskAt(int vehicleIdx, int taskPos) {
		AgentTask current = this.vehiclesFirstTask[vehicleIdx];
		int i = 0;
		while (current != null) {
			if (i == taskPos) {
				return current;
			}
			current = current.getNext();
			i++;
		}
		return null;
	}

	public void addTaskForVehicle(int vehicleIdx, AgentTask toAdd, AgentTask taskBeforeToAdd) {
		if (taskBeforeToAdd == null) {
			// Means first position
			toAdd.setNext(this.vehiclesFirstTask[vehicleIdx]);
			this.vehiclesFirstTask[vehicleIdx] = toAdd;
			recomputeCostWhenAddingTask(taskBeforeToAdd, toAdd, toAdd.getNext(), this.vehicles.get(vehicleIdx));
			if (toAdd.isPickup()) {
				weights[vehicleIdx] += toAdd.getTask().weight;
			}
			
			return;
		}
		
		if (this.vehiclesFirstTask[vehicleIdx] == null) {
			throw new IllegalStateException("Cannot add task not at the first place when no task for vehicle.");
		}
		
		if (toAdd.isPickup()) {
			weights[vehicleIdx] += toAdd.getTask().weight;
		}
		AgentTask current = this.vehiclesFirstTask[vehicleIdx];
		while (current != null) {
			if (current.equals(taskBeforeToAdd)) {
				AgentTask temp = current.getNext();
				current.setNext(toAdd);
				toAdd.setNext(temp);
				recomputeCostWhenAddingTask(current, toAdd, temp, this.vehicles.get(vehicleIdx));
				break;
			}
			current = current.getNext();
		}
	}
	
	private void recomputeCostWhenAddingTask(AgentTask lastTask, AgentTask toAdd, AgentTask next, Vehicle vehicle) {
		City lastCity = null;
		City city = toAdd.isPickup() ? toAdd.getTask().pickupCity : toAdd.getTask().deliveryCity;
		City nextCity = null;
		
		if (lastTask != null) {
			lastCity = lastTask.isPickup() ? lastTask.getTask().pickupCity : lastTask.getTask().deliveryCity;;
		}
		
		if (next != null) {
			nextCity = next.isPickup() ? next.getTask().pickupCity : next.getTask().deliveryCity;;
		}
		
		if (lastTask == null) {
			this.totalCost += (vehicle.getCurrentCity().distanceTo(city)) * vehicle.costPerKm();
			if (next == null) {
				// Nothing to do more
			}
			else {
				this.totalCost += (nextCity.distanceTo(city)) * vehicle.costPerKm();
				this.totalCost -= (vehicle.getCurrentCity().distanceTo(nextCity)) * vehicle.costPerKm();
			}
		}
		else {
			if (next == null) {
				this.totalCost += (lastCity.distanceTo(city)) * vehicle.costPerKm();
			}
			else {
				this.totalCost += (lastCity.distanceTo(city)) * vehicle.costPerKm();
				this.totalCost += (nextCity.distanceTo(city)) * vehicle.costPerKm();
				this.totalCost -= (lastCity.distanceTo(nextCity)) * vehicle.costPerKm();
			}
		}
		
	}

	// Return prev and the one we remove
	public List<AgentTask> removeTaskForVehicle(int vehicleIdx, Task task, boolean b) {
		AgentTask current = this.vehiclesFirstTask[vehicleIdx];
		AgentTask lastTask = null;
		while (current != null) {
			if (current.getTask().equals(task) && current.isPickup() == b) {
				if (lastTask == null) {
					this.vehiclesFirstTask[vehicleIdx] = current.getNext();
				}
				else {
					lastTask.setNext(current.getNext());
				}
				this.recomputeCostWhenRemovingTask(lastTask, current, current.getNext(), this.vehicles.get(vehicleIdx));
				if (current.isPickup()) {
					weights[vehicleIdx] -= current.getTask().weight;
				}
				current.setNext(null);
				List<AgentTask> toReturn = new ArrayList<AgentTask>();
				toReturn.add(lastTask);
				toReturn.add(current);
				return toReturn;
			}
			lastTask = current;
			current = current.getNext();
		}
		return null;
	}
	
	public List<AgentTask> removeTaskForVehicle(int vehicleIdx, AgentTask aTask) {
		return removeTaskForVehicle(vehicleIdx, aTask.getTask(), aTask.isPickup());
	}

	public void recomputeCostWhenRemovingTask(AgentTask lastTask, AgentTask current, AgentTask next, Vehicle vehicle) {
		City lastCity = null;
		City city = current.isPickup() ? current.getTask().pickupCity : current.getTask().deliveryCity;
		City nextCity = null;
		
		if (lastTask != null) {
			lastCity = lastTask.isPickup() ? lastTask.getTask().pickupCity : lastTask.getTask().deliveryCity;;
		}
		
		if (next != null) {
			nextCity = next.isPickup() ? next.getTask().pickupCity : next.getTask().deliveryCity;;
		}
		
		if (lastTask == null) {
			this.totalCost -= (vehicle.getCurrentCity().distanceTo(city)) * vehicle.costPerKm();
			if (next == null) {
				// Nothing to do more
				assert(this.totalCost == 0d);
			}
			else {
				this.totalCost -= (nextCity.distanceTo(city)) * vehicle.costPerKm();
				this.totalCost += (vehicle.getCurrentCity().distanceTo(nextCity)) * vehicle.costPerKm();
			}
		}
		else {
			if (next == null) {
				this.totalCost -= (lastCity.distanceTo(city)) * vehicle.costPerKm();
			}
			else {
				this.totalCost -= (lastCity.distanceTo(city)) * vehicle.costPerKm();
				this.totalCost -= (nextCity.distanceTo(city)) * vehicle.costPerKm();
				this.totalCost += (lastCity.distanceTo(nextCity)) * vehicle.costPerKm();
			}
		}
		
	}
	
}
