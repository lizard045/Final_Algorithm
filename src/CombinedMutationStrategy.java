import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * CombinedMutationStrategy類別：實現組合突變策略
 * 對分配部分使用智慧突變，對排序部分使用交換突變
 */
public class CombinedMutationStrategy implements MutationStrategy {
    @Override
    public void mutate(Schedule schedule, double mutationRate, DAG dag, Random random) {
        // 1. 對分配部分執行智慧突變
        smartMutateAssignments(schedule, mutationRate, dag, random);
        
        // 2. 對排序部分執行交換突變
        swapMutateOrder(schedule, mutationRate, random);
    }
    
    private void smartMutateAssignments(Schedule schedule, double mutationRate, DAG dag, Random random) {
        int[] chromosome = schedule.getChromosome();
        for (int i = 0; i < chromosome.length; i++) {
            if (random.nextDouble() < mutationRate) {
                // Smart mutation: prefer processors with lower computation cost for this task
                int currentTask = i;
                int currentProc = chromosome[currentTask];
                
                double minCost = Double.MAX_VALUE;
                int bestProc = currentProc;

                for (int p = 0; p < dag.getProcessorCount(); p++) {
                    double cost = dag.getTask(currentTask).getComputationCost(p);
                    if (cost < minCost) {
                        minCost = cost;
                        bestProc = p;
                    }
                }
                
                // Mutate to the best processor, or a random one if multiple are equally good
                if (bestProc != currentProc) {
                    chromosome[currentTask] = bestProc;
                } else {
                    // If already on the best, mutate to a random different one
                    int newProc = random.nextInt(dag.getProcessorCount());
                    while (newProc == currentProc) {
                        newProc = random.nextInt(dag.getProcessorCount());
                    }
                    chromosome[currentTask] = newProc;
                }
            }
        }
    }
    
    private void swapMutateOrder(Schedule schedule, double mutationRate, Random random) {
        List<Integer> order = schedule.getTaskOrder();
        if (order == null || order.size() < 2) return;

        // 以同樣的突變率決定是否要對排序進行一次突變
        if (random.nextDouble() < mutationRate) {
            int index1 = random.nextInt(order.size());
            int index2 = random.nextInt(order.size());
            
            // 確保交換的是不同的位置
            while (index1 == index2) {
                index2 = random.nextInt(order.size());
            }

            Collections.swap(order, index1, index2);
            schedule.setTaskOrder(order); // 觸發重新評估
        }
    }
} 