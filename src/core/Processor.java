package core;

/**
 * Processor類別：儲存處理器資訊
 * 包含處理器ID、任務列表、完成時間等資訊
 */
public class Processor {
    private final int processorId;
    private double readyTime; // 處理器準備時間
    
    public Processor(int processorId) {
        this.processorId = processorId;
        this.readyTime = 0.0;
    }
    
    // Getters and Setters
    public int getProcessorId() {
        return processorId;
    }
    
    public double getReadyTime() {
        return readyTime;
    }
    
    public void setReadyTime(double readyTime) {
        this.readyTime = readyTime;
    }
    
    @Override
    public String toString() {
        return "Processor{" +
                "processorId=" + processorId +
                ", readyTime=" + readyTime +
                '}';
    }
} 