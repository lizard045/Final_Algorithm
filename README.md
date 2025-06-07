# GA任務調度演算法系統

## 概述

這是一個使用遺傳演算法(Genetic Algorithm, GA)來解決異質性分散式計算環境中任務調度問題的Java實現。系統能夠讀取DAG(有向無環圖)格式的任務檔案，並使用GA演算法找到最佳的任務到處理器分配方案，以最小化總執行時間(makespan)。

## 系統架構

### 核心組件

1. **DAGReader.java** - DAG檔案讀取器
   - 解析.dag格式檔案
   - 提取處理器數量、任務數量、通訊速率矩陣、計算成本矩陣和任務相依性

2. **Individual.java** - 個體表示類別
   - 代表一個任務調度方案
   - 包含染色體編碼、交叉、突變操作

3. **FitnessEvaluator.java** - 適應度評估器
   - 計算調度方案的總執行時間
   - 考慮任務相依性和通訊成本

4. **GeneticAlgorithm.java** - 遺傳演算法核心
   - 實現選擇、交叉、突變、菁英保留等GA操作
   - 支援多種初始化策略

5. **StatisticsAnalyzer.java** - 統計分析器
   - 收集和分析實驗結果
   - 計算Best、Worst、Average、標準差等統計指標

6. **GATaskScheduler.java** - 主程式
   - 整合所有組件
   - 執行多次實驗並輸出結果

## 檔案結構

```
project/
├── src/                    # 源代碼目錄
│   ├── DAGReader.java
│   ├── Individual.java
│   ├── FitnessEvaluator.java
│   ├── GeneticAlgorithm.java
│   ├── StatisticsAnalyzer.java
│   └── GATaskScheduler.java
├── bin/                    # 編譯後的class檔案
├── n4_00.dag              # 測試資料檔案 (異質性 0.0)
├── n4_02.dag              # 測試資料檔案 (異質性 0.2)
├── n4_04.dag              # 測試資料檔案 (異質性 0.4)
├── n4_06.dag              # 測試資料檔案 (異質性 0.6)
└── README.md              # 說明檔案
```

## 編譯和執行

### 編譯

```bash
# 編譯所有Java檔案到bin目錄
javac -d bin src/*.java
```

### 執行

```bash
# 切換到bin目錄
cd bin

# 複製DAG檔案到bin目錄（如果尚未複製）
copy ../*.dag .

# 執行主程式
java GATaskScheduler
```

## DAG檔案格式

DAG檔案包含以下資訊：

1. **基本參數**：處理器數量、任務數量、邊數量
2. **通訊速率矩陣**：處理器間的通訊速率
3. **計算成本矩陣**：每個任務在每個處理器上的執行時間
4. **任務相依性**：任務間的資料傳輸關係

## GA演算法參數

當前系統使用的預設參數：

- **族群大小**：50
- **最大世代數**：100
- **交叉率**：0.8
- **突變率**：0.1
- **菁英個體數**：3
- **錦標賽大小**：3

## 實驗結果

系統會對每個測試檔案執行多次實驗，並輸出以下統計指標：

- **Best**：最佳適應度值
- **Worst**：最差適應度值
- **Avg**：平均適應度值
- **sd**：標準差
- **Avg. Running Time**：平均執行時間（秒）

### 範例結果

| Heterogeneity | Best  | Worst | Avg   | sd    | Avg. Running Time (s) |
|---------------|-------|-------|-------|-------|----------------------|
| 0.0           | 490.0 | 490.0 | 490.0 | 0.00  | 0.0593              |
| 0.2           | 466.6 | 525.8 | 501.6 | 31.06 | 0.0267              |
| 0.4           | 465.6 | 510.9 | 481.6 | 25.41 | 0.0193              |
| 0.6           | 470.1 | 506.7 | 493.4 | 20.25 | 0.0193              |

## 自訂使用

### 修改GA參數

在`GATaskScheduler.java`中的`runExperiments`方法內修改：

```java
ga.setParameters(
    populationSize,    // 族群大小
    maxGenerations,    // 最大世代數
    crossoverRate,     // 交叉率
    mutationRate,      // 突變率
    eliteSize,         // 菁英數
    tournamentSize     // 錦標賽大小
);
```

### 添加新的測試檔案

1. 將新的.dag檔案放在專案根目錄
2. 在`GATaskScheduler.java`的`main`方法中添加檔案名稱到`testFiles`陣列
3. 相應地更新`heterogeneityLevels`陣列

### 詳細分析模式

可以使用`detailedAnalysis`方法對單一檔案進行詳細分析：

```java
GATaskScheduler scheduler = new GATaskScheduler();
scheduler.detailedAnalysis("n4_00.dag");
```

## 演算法特點

1. **多樣化初始化**：結合隨機、啟發式和混合初始化策略
2. **錦標賽選擇**：平衡選擇壓力和多樣性
3. **菁英保留**：確保最佳解不會丟失
4. **適應性終止**：當收斂時提早終止以節省計算時間
5. **統計分析**：完整的實驗結果統計和分析

## 系統需求

- Java 8 或更高版本
- 支援命令列執行環境

## 注意事項

1. DAG檔案必須使用正確的格式
2. 檔案編碼可能影響中文註解的顯示，但不影響數據解析
3. 較大的問題實例可能需要調整GA參數以獲得更好的結果
4. 執行時間會隨著族群大小和世代數的增加而增長

## 擴展功能

系統支援以下擴展：

1. **參數敏感度分析**：測試不同參數組合的效果
2. **收斂曲線分析**：觀察演算法的收斂過程
3. **多目標優化**：可擴展為考慮多個優化目標
4. **其他演算法比較**：可添加其他啟發式演算法進行比較

## 作者

本系統實現了一個完整的GA任務調度解決方案，適用於異質性分散式計算環境的任務調度優化問題。 