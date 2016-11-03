package template;

//the list of imports
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import logist.LogistSettings;
import logist.agent.Agent;
import logist.behavior.CentralizedBehavior;
import logist.config.Parsers;
import logist.plan.Plan;
import logist.simulation.Vehicle;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.task.TaskSet;
import logist.topology.Topology;
import logist.topology.Topology.City;

/**
 * A very simple auction agent that assigns all tasks to its first vehicle and
 * handles them sequentially.
 *
 */
@SuppressWarnings("unused")
public class CentralizedTemplate implements CentralizedBehavior {

	private Topology topology;
    private TaskDistribution distribution;
    private Agent agent;
    private long timeout_setup;
    private long timeout_plan;

    @Override
    public void setup(Topology topology, TaskDistribution distribution,
            Agent agent) {

        // this code is used to get the timeouts
        LogistSettings ls = null;
        try {
            ls = Parsers.parseSettings("config/settings_default.xml");
        }
        catch (Exception exc) {
            System.out.println("There was a problem loading the configuration file.");
        }

        // the setup method cannot last more than timeout_setup milliseconds
        timeout_setup = ls.get(LogistSettings.TimeoutKey.SETUP);
        // the plan method cannot execute more than timeout_plan milliseconds
        timeout_plan = ls.get(LogistSettings.TimeoutKey.PLAN);

        this.topology = topology;
        this.distribution = distribution;
        this.agent = agent;
    }

    @Override
    public List<Plan> plan(List<Vehicle> vehicles, TaskSet tasks) {
        long time_start = System.currentTimeMillis();

        for (Vehicle v : vehicles) {
        	System.out.println(v.capacity());
        }

        List<Plan> plans = localSearchPlan(vehicles, tasks);

        long time_end = System.currentTimeMillis();
        long duration = time_end - time_start;
        System.out.println("The plan was generated in "+duration+" milliseconds.");

        return plans;
    }

    private List<Plan> localSearchPlan(List<Vehicle> vehicles, TaskSet tasks) {
    	// Create first solution

    	System.out.println("There are " + tasks.size() + " tasks");
    	Solution currentSolution = createInitSolution(vehicles, tasks);
    	System.out.println("Init sol created");
    	Solution bestSolution = currentSolution;

    	int iteration = 0;
    	int maxIteration = 1000000;

    	do {

    		iteration++;
    		System.out.println("new iteration " +iteration);

    		Solution randomN = null;
    		
    		if (Math.random() < 0.5) {
    			// Change task order
    			randomN = changingTaskOrder(currentSolution);
    		}
    		else {
    			// Change vehicle
    			randomN = changingVehicle(currentSolution);
    		}
    		
    		if (P(currentSolution, randomN, ((double) iteration) /maxIteration) >= Math.random()) {
    			currentSolution = randomN;
    			if (currentSolution.getTotalCost() < bestSolution.getTotalCost()) {
    				bestSolution = currentSolution;
    			}
    		}
    		
    		
    	} while (iteration < maxIteration);

		System.out.println("Best solution cost: " + bestSolution.getTotalCost() + ", with iteration " + iteration);
    	printSolution(bestSolution, true);

    	return createPlanFromSolution(bestSolution);
    }

    private double P(Solution currentSolution, Solution newSolution, double timeRatio) {
    	
    	if (currentSolution.getTotalCost() >= newSolution.getTotalCost()) {
    		return 1;
    	}
    	
    	return (Math.exp(-(newSolution.getTotalCost() - currentSolution.getTotalCost()) / timeRatio));
    	
	}

	private List<Plan> createPlanFromSolution(Solution solution) {
		List<Plan> toReturn = new ArrayList<Plan>();
		for (int i = 0; i < solution.getVehiclesFirstTask().length; i++) {
			AgentTask current = solution.getVehiclesFirstTask()[i];
			Plan plan = new Plan(solution.getVehicles().get(i).getCurrentCity());
			City currentCity = solution.getVehicles().get(i).getCurrentCity();
			while (current != null) {
				if (current.isPickup()) {
					for (City c : currentCity.pathTo(current.getTask().pickupCity)) {
						plan.appendMove(c);
					}
					plan.appendPickup(current.getTask());
					currentCity = current.getTask().pickupCity;
				}
				else {
					for (City c : currentCity.pathTo(current.getTask().deliveryCity)) {
						plan.appendMove(c);
					}
					plan.appendDelivery(current.getTask());
					currentCity = current.getTask().deliveryCity;
				}
				current = current.getNext();
			}
			toReturn.add(plan);
		}

		return toReturn;
	}

	private Solution findBestSolution(List<Solution> n) {
		Solution toReturn = null;
		double minCost = Double.MAX_VALUE;
		for (Solution sol : n) {
			if (sol.getTotalCost() < minCost) {
				minCost = sol.getTotalCost();
				toReturn = sol;
			}
		}

		n.remove(toReturn);
		return toReturn;
	}

	private Solution changingTaskOrder(Solution oldSolution) {
		
		Solution toReturn = null;
		
		while (toReturn == null) {
			int vehicleIdx = (int) (Math.random() * oldSolution.getVehiclesFirstTask().length);
			int firstTaskIdx = (int) (Math.random() * oldSolution.getTaskNumber(vehicleIdx));
			int secondTaskIdx = (int) (Math.random() * oldSolution.getTaskNumber(vehicleIdx));
			if (firstTaskIdx == secondTaskIdx || oldSolution.getTaskNumber(vehicleIdx) <= 3) {
				continue;
			}
			
			Solution sol = oldSolution.clone();
			AgentTask firstTask = sol.getAgentTaskAt(vehicleIdx, firstTaskIdx);
			AgentTask secondTask = sol.getAgentTaskAt(vehicleIdx, secondTaskIdx);
			if (firstTask == null || secondTask == null) {
				throw new IllegalStateException("New solution does not correspond to the old one.");
			}

			// Exchange
			AgentTask beforeNewOther = secondTask;
			if (!secondTask.equals(firstTask.getNext())) {
				// Get the element before the one we removed
				beforeNewOther = sol.removeTaskForVehicle(vehicleIdx, secondTask).get(0);
				sol.addTaskForVehicle(vehicleIdx, secondTask, firstTask);
			}
			sol.removeTaskForVehicle(vehicleIdx, firstTask);
			sol.addTaskForVehicle(vehicleIdx, firstTask, beforeNewOther);

			// Set first of vehicle if needed
			if (sol.checkCorrectSolution()) {
				toReturn = sol;
			}
			
		}


		return toReturn;
	}

	private Solution changingVehicle(Solution oldSolution) {

		Solution toReturn = null;
		while (toReturn == null) {
			int firstVIdx = (int) (Math.random() * oldSolution.getVehiclesFirstTask().length);
			int secondVIdx = (int) (Math.random() * oldSolution.getVehiclesFirstTask().length);
			int taskIdx = (int) (Math.random() * oldSolution.getTaskNumber(firstVIdx));
			if (firstVIdx == secondVIdx || oldSolution.getTaskNumber(firstVIdx) < 2) {
				continue;
			}
					
			Solution sol = oldSolution.clone();
			AgentTask taskToMove = sol.getAgentTaskAt(firstVIdx, taskIdx);
			
			sol.removeTaskForVehicle(firstVIdx, taskToMove);
			AgentTask correspondingTask = sol.removeTaskForVehicle(firstVIdx, taskToMove.getTask(), !taskToMove.isPickup()).get(1);
			
			if (taskToMove.isPickup()) {
				sol.addTaskForVehicle(secondVIdx, correspondingTask, null);
				sol.addTaskForVehicle(secondVIdx, taskToMove, null);
			}
			else {
				sol.addTaskForVehicle(secondVIdx, taskToMove, null);
				sol.addTaskForVehicle(secondVIdx, correspondingTask, null);
			}

			if (sol.checkCorrectSolution()) {
				toReturn = sol;
			}
		}

		return toReturn;
	}

	private Solution createInitSolution(List<Vehicle> vehicles, TaskSet tasks) {
    	int vehiclesIdx = 0;
    	AgentTask[] lastTasks = new AgentTask[vehicles.size()];
    	int[] weights = new int[vehicles.size()];
    	int[] taskCounter = new int[vehicles.size()];
    	AgentTask[] vehiclesFirstTask = new AgentTask[vehicles.size()];
    	double totalCost = 0.0;

    	for (Task task : tasks) {

    		Vehicle currentVehicle = vehicles.get(vehiclesIdx);

    		AgentTask aTask1 = new AgentTask(task, true);
    		AgentTask aTask2 = new AgentTask(task, false);
    		aTask1.setNext(aTask2);
    		taskCounter[vehiclesIdx] += 2;

    		if (currentVehicle.capacity() < task.weight) {
    			System.out.println("Unsolvable situation: one task is too heavy for one vehicle.");
    			return null;
    		}

    		if (lastTasks[vehiclesIdx] == null) {
    			// New vehicle
    			// Should we add the cost from current city to first city?
    			vehiclesFirstTask[vehiclesIdx] = aTask1;
    			totalCost += currentVehicle.getCurrentCity().distanceTo(task.pickupCity) * currentVehicle.costPerKm();
    		}
    		else {
    			lastTasks[vehiclesIdx].setNext(aTask1);
    			totalCost += (lastTasks[vehiclesIdx].getTask().deliveryCity.distanceTo(task.pickupCity)) * currentVehicle.costPerKm();
    		}

    		totalCost += (task.pickupCity.distanceTo(task.deliveryCity)) * currentVehicle.costPerKm();
    		lastTasks[vehiclesIdx] = aTask2;

    		//vehiclesIdx = (vehiclesIdx + 1) % vehicles.size();
    	}

    	System.out.println("Cost of init solution: " + totalCost);

    	return new Solution(totalCost, weights, vehiclesFirstTask, vehicles, taskCounter);
    }

    private Plan naivePlan(Vehicle vehicle, TaskSet tasks) {
        City current = vehicle.getCurrentCity();
        Plan plan = new Plan(current);

        for (Task task : tasks) {
            // move: current city => pickup location
            for (City city : current.pathTo(task.pickupCity)) {
                plan.appendMove(city);
            }

            plan.appendPickup(task);

            // move: pickup location => delivery location
            for (City city : task.path()) {
                plan.appendMove(city);
            }

            plan.appendDelivery(task);

            // set current city
            current = task.deliveryCity;
        }
        return plan;
    }

    private void printSolution(Solution sol, boolean id) {
    	for (int i = 0; i < sol.getWeights().length; i++) {
    		AgentTask current = sol.getVehiclesFirstTask()[i];
    		System.out.println("Vehicle " + i);
    		while (current != null) {
    			System.out.print(current.isPickup() ? "pickup" : "deliver");
    			System.out.print(":");
    			if (id) {
    				if (current.isPickup()) {
            			System.out.print(current.getTask().pickupCity + "(" + current.getTask().id + ") ; ");
    				}
    				else {
            			System.out.print(current.getTask().deliveryCity + "(" + current.getTask().id + ") ; ");
    				}

    			}
    			else {
    				if (current.isPickup()) {
            			System.out.print(current.getTask().pickupCity + "(" + current.getTask().weight + ") ; ");
    				}
    				else {
            			System.out.print(current.getTask().deliveryCity + "(" + current.getTask().weight + ") ; ");
    				}
    			}
    			current = current.getNext();
    		}
    		System.out.println();
    	}
    }
}
