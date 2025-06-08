package ga;

import core.DAG;
import core.Schedule;
import java.util.Random;

/**
 * MutationStrategy介面：定義了突變操作的策略
 */
public interface MutationStrategy {
    /**
     * 對一個排程執行突變操作
     * @param schedule 要進行突變的排程
     * @param mutationRate 突變率
     * @param dag DAG物件，用於獲取任務和處理器資訊
     * @param random 隨機數生成器
     */
    void mutate(Schedule schedule, double mutationRate, DAG dag, Random random);
} 