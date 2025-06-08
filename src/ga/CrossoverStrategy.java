package ga;

import core.DAG;
import core.Schedule;
import java.util.Random;

/**
 * CrossoverStrategy介面：定義了交配操作的策略
 */
public interface CrossoverStrategy {
    /**
     * 對兩個親代執行交配操作以產生一個子代
     * @param parent1 親代1
     * @param parent2 親代2
     * @param dag DAG物件，用於獲取任務數量等資訊
     * @param random 隨機數生成器
     * @return 新產生的子代
     */
    Schedule crossover(Schedule parent1, Schedule parent2, DAG dag, Random random);
} 