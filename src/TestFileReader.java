import java.io.*;

public class TestFileReader {
    public static void main(String[] args) {
        try {
            BufferedReader reader = new BufferedReader(new FileReader("n4_00.dag"));
            String line;
            int lineNumber = 1;
            
            System.out.println("檔案內容分析:");
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                System.out.printf("行 %d: [%s] 長度=%d, 是否為空=%b, 開始字元=%s\n", 
                                lineNumber, trimmed, trimmed.length(), trimmed.isEmpty(),
                                trimmed.isEmpty() ? "N/A" : "'" + trimmed.charAt(0) + "'");
                
                if (!trimmed.startsWith("/*") && !trimmed.startsWith("*/") && 
                    !trimmed.isEmpty() && !trimmed.contains("=")) {
                    System.out.println("  -> 這是數據行");
                    try {
                        int value = Integer.parseInt(trimmed);
                        System.out.println("  -> 成功解析為整數: " + value);
                        break;
                    } catch (NumberFormatException e) {
                        System.out.println("  -> 無法解析為整數: " + e.getMessage());
                    }
                }
                lineNumber++;
            }
            reader.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
} 