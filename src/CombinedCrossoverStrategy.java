import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * CombinedCrossoverStrategy類別：實現組合交配策略
 * 對分配部分使用均勻交配，對排序部分使用Order Crossover (OX1)
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

        // 2. 對排序部分執行Order Crossover (OX1)
        List<Integer> childOrder = orderCrossover(parent1.getTaskOrder(), parent2.getTaskOrder(), random);

        // 3. 創建新的子代 Schedule
        Schedule child = new Schedule(dag, childAssignment);
        child.setTaskOrder(childOrder);
        
        return child;
    }

    private List<Integer> orderCrossover(List<Integer> order1, List<Integer> order2, Random random) {
        int size = order1.size();
        List<Integer> childOrder = new ArrayList<>(Collections.nCopies(size, null));

        // 隨機選擇一個連續的子序列
        int start = random.nextInt(size);
        int end = random.nextInt(size);
        if (start > end) {
            int temp = start;
            start = end;
            end = temp;
        }

        // 將 parent1 的子序列複製到子代
        Set<Integer> copiedTasks = new HashSet<>();
        for (int i = start; i <= end; i++) {
            int task = order1.get(i);
            childOrder.set(i, task);
            copiedTasks.add(task);
        }

        // 從 parent2 填補剩餘的空位
        int currentPos = (end + 1) % size;
        for (int task : order2) {
            if (!copiedTasks.contains(task)) {
                // 找到下一個空位
                while (childOrder.get(currentPos) != null) {
                    currentPos = (currentPos + 1) % size;
                }
                childOrder.set(currentPos, task);
            }
        }
        
        return childOrder;
    }
} 