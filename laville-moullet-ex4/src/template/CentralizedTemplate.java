package template;

//the list of imports
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import logist.LogistSettings;
import logist.Measures;
import logist.behavior.AuctionBehavior;
import logist.behavior.CentralizedBehavior;
import logist.agent.Agent;
import logist.config.Parsers;
import logist.simulation.Vehicle;
import logist.plan.Plan;
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

        /*
//		System.out.println("Agent " + agent.id() + " has tasks " + tasks);
        Plan planVehicle1 = naivePlan(vehicles.get(0), tasks);

        List<Plan> plans = new ArrayList<Plan>();
        plans.add(planVehicle1);
        while (plans.size() < vehicles.size()) {
            plans.add(Plan.EMPTY);
        }
        */

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
    	Solution oldSolution = null;

    	int iteration = 0;

    	do {

    		iteration++;

    		oldSolution = currentSolution;
    		List<Solution> n = chooseNeighbours(oldSolution);
    		//System.out.println("Neighbours chosen: " + n.size());

    		currentSolution = findBestSolution(n);
    		//System.out.println("Best solution found");
    		//System.out.println("Diff: " + (oldSolution.getTotalCost() - currentSolution.getTotalCost()));

    	} while (oldSolution.getTotalCost() > currentSolution.getTotalCost());

		System.out.println("Best solution cost: " + oldSolution.getTotalCost() + ", with iteration " + iteration);
    	printSolution(oldSolution, true);

    	return createPlanFromSolution(oldSolution);
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

		/*
		System.out.println("Best solution weights: " + Arrays.toString(toReturn.getWeights()));
		System.out.println("Best solution cost: " + toReturn.getTotalCost());
		printSolution(toReturn);
		*/

		return toReturn;
	}

	private List<Solution> chooseNeighbours(Solution oldSolution) {
		List<Solution> toReturn = new ArrayList<Solution>();

		List<Solution> newSolutions = new ArrayList<Solution>();

		// ---------------------- Exchange task within a vehicle ----------------------

		// Create new solutions (only copy of old one for now)
		for (int i = 0; i < oldSolution.getWeights().length; i++) {
			AgentTask current = oldSolution.getVehiclesFirstTask()[i];

			while (current != null && current.getNext() != null) {
				AgentTask other = current.getNext();
				while (other != null) {
					Solution newSolution = oldSolution.clone();
					newSolutions.add(newSolution);
					other = other.getNext();
				}
				current = current.getNext();
			}
		}


		int solIdx = 0;
		for (int i = 0; i < oldSolution.getWeights().length; i++) {
			AgentTask current = oldSolution.getVehiclesFirstTask()[i];

			// Exchange tasks in solution
			boolean first = true;

			AgentTask newCurrent = null;
			AgentTask newOther = null;
			int a = 0;

			while (current != null && current.getNext() != null) {
				int b = a + 1;
				AgentTask other = current.getNext();
				while (other != null) {
					Solution sol = newSolutions.get(solIdx);
					newCurrent = sol.getAgentTaskAt(i, a);
					newOther = sol.getAgentTaskAt(i, b);

					if (newCurrent == null || newOther == null) {
						throw new IllegalStateException("New solution does not correspond to the old one.");
					}

					// Exchange
					AgentTask beforeNewOther = newOther;
					if (!newOther.equals(newCurrent.getNext())) {
						// Get the element before the one we removed
						beforeNewOther = sol.removeTaskForVehicle(i, newOther).get(0);
						sol.addTaskForVehicle(i, newOther, newCurrent);
					}
					sol.removeTaskForVehicle(i, newCurrent);
					sol.addTaskForVehicle(i, newCurrent, beforeNewOther);

					// Set first of vehicle if needed
					solIdx++;
					if (sol.checkCorrectSolution()) {

						//System.out.println("ADD!!! " + newCurrent.getTask().id);
						//printSolution(sol);

						toReturn.add(sol);
					}
					other = other.getNext();
					b++;
				}
				first = false;
				a++;
				current = current.getNext();
			}
		}

		/*
		System.out.println("---------------------------------------------------------");
		for (Solution s : toReturn) {
			printSolution(s);
		}
		*/

		// ---------------------- Change task of vehicle ----------------------

		newSolutions = new ArrayList<Solution>();

		// Create new solutions (only copy of old one for now)
		for (int i = 0; i < oldSolution.getWeights().length; i++) {
			for (int j = 0; j < oldSolution.getWeights().length; j++) {
				if (i != j) {
					Solution newSolution = oldSolution.clone();
					newSolutions.add(newSolution);
				}
			}
		}

		for (int i = 0; i < oldSolution.getWeights().length; i++) {
			for (int j = 0; j < oldSolution.getWeights().length; j++) {
				if (i != j) {
					Solution sol = newSolutions.get(i * oldSolution.getWeights().length + j - (i + (j > i ? 1 : 0)));
					AgentTask firstTask = sol.getVehiclesFirstTask()[i];
					if (firstTask == null) {
						continue; // TODO break ?
					}

					if (!firstTask.isPickup()) {
						throw new IllegalStateException("Cannot have a deliver first for a vehicle in a solution.");
					}

					if (firstTask.getNext() == null) {
						throw new IllegalStateException("Cannot have a pickup but no delivery for the same vehicle.");
					}

					sol.removeTaskForVehicle(i, firstTask);
					AgentTask correspondingDeliver = sol.removeTaskForVehicle(i, firstTask.getTask(), !firstTask.isPickup()).get(1);

					sol.addTaskForVehicle(j, correspondingDeliver, null);
					sol.addTaskForVehicle(j, firstTask, null);

					// TODO useless
					if (sol.checkCorrectSolution()) {
						toReturn.add(sol);
					} else {
						throw new IllegalStateException("Can't have a task bigger than the capacity of a vehicle");
					}
				}
			}

		}

		return toReturn;
	}

	private Solution createInitSolution(List<Vehicle> vehicles, TaskSet tasks) {
    	int vehiclesIdx = 3;
    	AgentTask lastTask = null;
    	int[] weights = new int[vehicles.size()];
    	AgentTask[] vehiclesFirstTask = new AgentTask[vehicles.size()];
    	double totalCost = 0.0;

    	Vehicle currentVehicle = vehicles.get(vehiclesIdx);

    	for (Task task : tasks) {

    		AgentTask aTask1 = new AgentTask(task, true);
    		AgentTask aTask2 = new AgentTask(task, false);
    		aTask1.setNext(aTask2);

    		if (currentVehicle.capacity() < task.weight) {
    			System.out.println("Unsolvable situation: one task is too heavy for one vehicle.");
    			return null;
    		}

    		if (lastTask == null) {
    			// New vehicle
    			// Should we add the cost from current city to first city?
    			vehiclesFirstTask[vehiclesIdx] = aTask1;
    			totalCost += currentVehicle.getCurrentCity().distanceTo(task.pickupCity) * currentVehicle.costPerKm();
    		}
    		else {
    			lastTask.setNext(aTask1);
    			totalCost += (lastTask.getTask().deliveryCity.distanceTo(task.pickupCity)) * currentVehicle.costPerKm();
    		}

    		totalCost += (task.pickupCity.distanceTo(task.deliveryCity)) * currentVehicle.costPerKm();
    		lastTask = aTask2;
    	}

    	System.out.println("Cost of init solution: " + totalCost);

    	return new Solution(totalCost, weights, vehiclesFirstTask, vehicles);
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
