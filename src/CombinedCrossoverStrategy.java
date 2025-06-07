import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * CombinedCrossoverStrategy類別：實現組合交配策略
 * 對分配部分使用均勻交配，排序部分則直接繼承自父代
 */
public class CombinedCrossoverStrategy implements CrossoverStrategy {
    @Override
    public Schedule crossover(Schedule parent1, Schedule parent2, DAG dag, Random random) {
        // 1. 對分配部分執行均勻交配
        int[] childAssignment = new int[dag.getTaskCount()];
        int[] p1Assignment = parent1.getChromosome();
        int[] p2Assignment = parent2.getChromosome();

        for (int i = 0; i < dag.getTaskCount(); i++) {
            if (random.nextBoolean()) {
                childAssignment[i] = p1Assignment[i];
            } else {
                childAssignment[i] = p2Assignment[i];
            }
        }

        // 2. **核心簡化**：直接繼承父代的任務順序（因為所有順序都應相同）
        List<Integer> childOrder = new ArrayList<>(parent1.getTaskOrder());

        // 3. 創建新的子代 Schedule
        Schedule child = new Schedule(dag, childAssignment);
        child.setTaskOrder(childOrder);
        
        return child;
    }
} 