package template;

//the list of imports
import java.util.ArrayList;
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
            ls = Parsers.parseSettings("config\\settings_default.xml");
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
    	
    	Solution currentSolution = createInitSolution(vehicles, tasks);
    	Solution oldSolution = null;
    	
    	do {
    		oldSolution = currentSolution;
    		List<Solution> n = chooseNeighbours(oldSolution);
    		
    		currentSolution = findBestSolution(n);
    		
    	} while(oldSolution.getTotalCost() > currentSolution.getTotalCost());
    	
    	return createPlanFromSolution(oldSolution);
    }
    
    private Solution findBestSolution(List<Solution> n) {
		// TODO Auto-generated method stub
		return null;
	}

	private List<Solution> chooseNeighbours(Solution oldSolution) {
		// TODO Auto-generated method stub
		return null;
	}

	private Solution createInitSolution(List<Vehicle> vehicles, TaskSet tasks) {
    	int vehiclesIdx = 0;
    	AgentTask lastTask = null;
    	int[] weights = new int[vehicles.size()];
    	AgentTask[] vehiclesFirstTask = new AgentTask[vehicles.size()];
    	double totalCost = 0.0;
    	
    	for (Task task : tasks) {
    		if (weights[vehiclesIdx] + task.weight > vehicles.get(vehiclesIdx).capacity()) {
    			vehiclesIdx++;
    			lastTask = null;
    			if (vehiclesIdx >= vehicles.size()) {
    				System.out.println("Unsolvable situation: tasks are too heavy for vehicle.");
    				return null;
    			}
    		}
    		
    		Vehicle currentVehicle = vehicles.get(vehiclesIdx);
    		
    		AgentTask aTask1 = new AgentTask(task, true);
    		AgentTask aTask2 = new AgentTask(task, false);
    		aTask1.setNext(aTask2);
    		
    		if (currentVehicle.capacity() < task.weight) {
    			System.out.println("Unsolvable situation: one task is too heavy for one vehicle.");
    			return null;
    		}
    		
    		if (lastTask == null) {
    			// New vehicle
    			vehiclesFirstTask[vehiclesIdx] = aTask1;
    		}
    		else {
    			lastTask.setNext(aTask1);
    			totalCost += (lastTask.getTask().deliveryCity.distanceTo(task.pickupCity)) * currentVehicle.costPerKm();
    		}
    		
    		totalCost += (task.pickupCity.distanceTo(task.deliveryCity)) * currentVehicle.costPerKm();
    		lastTask = aTask2;
    	}
    	
    	return new Solution(totalCost, weights, vehiclesFirstTask);
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
}
