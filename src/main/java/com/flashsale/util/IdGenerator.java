package com.flashsale.util;

import org.springframework.stereotype.Component;

/**
 * 分布式 ID 生成器工具类
 * 
 * 使用雪花算法（Snowflake）生成全局唯一 ID
 * 
 * 雪花算法结构：
 * - 1 bit: 符号位（固定为 0）
 * - 41 bits: 时间戳（毫秒级，可用 69 年）
 * - 10 bits: 机器 ID（支持 1024 台机器）
 * - 12 bits: 序列号（每毫秒可生成 4096 个 ID）
 * 
 * 面试考点：
 * 1. 为什么不用数据库自增 ID？
 *    - 自增 ID 暴露业务信息（订单量、用户量）
 *    - 分库分表后 ID 冲突
 *    - 性能瓶颈（单点写入）
 * 2. UUID 的缺点？
 *    - 太长（36 位）
 *    - 无序，影响 B+ 树性能
 *    - 无业务含义
 * 
 * @author XXJ
 * @since 2026-06-05
 */
@Component
public class IdGenerator {

    /**
     * 起始时间戳（2026-01-01 00:00:00 UTC）
     */
    private static final long START_TIMESTAMP = 1735689600000L;

    /**
     * 机器 ID 位数
     */
    private static final long MACHINE_BIT = 10L;

    /**
     * 序列号位数
     */
    private static final long SEQUENCE_BIT = 12L;

    /**
     * 机器 ID 最大值
     */
    private static final long MAX_MACHINE_NUM = -1L ^ (-1L << MACHINE_BIT);

    /**
     * 序列号最大值
     */
    private static final long MAX_SEQUENCE = -1L ^ (-1L << SEQUENCE_BIT);

    /**
     * 机器 ID 左移位数
     */
    private static final long MACHINE_LEFT = SEQUENCE_BIT;

    /**
     * 时间戳左移位数
     */
    private static final long TIMESTAMP_LEFT = SEQUENCE_BIT + MACHINE_BIT;

    /**
     * 机器 ID（实际项目中应从配置文件读取）
     */
    private long machineId = 1L;

    /**
     * 序列号
     */
    private long sequence = 0L;

    /**
     * 上次时间戳
     */
    private long lastTimestamp = -1L;

    /**
     * 生成下一个 ID
     * 
     * @return 分布式唯一 ID
     */
    public synchronized Long nextId() {
        long timestamp = System.currentTimeMillis();

        // 检查时钟是否回拨
        if (timestamp < lastTimestamp) {
            throw new RuntimeException("时钟回拨，拒绝生成 ID");
        }

        // 如果同一毫秒，序列号自增
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            // 如果序列号溢出，等待下一毫秒
            if (sequence == 0) {
                timestamp = waitNextMillis(lastTimestamp);
            }
        } else {
            // 不同毫秒，序列号重置为 0
            sequence = 0L;
        }

        lastTimestamp = timestamp;

        // 组合生成 ID
        return ((timestamp - START_TIMESTAMP) << TIMESTAMP_LEFT)
                | (machineId << MACHINE_LEFT)
                | sequence;
    }

    /**
     * 生成订单编号（格式：年月日 +8 位序列号）
     * 
     * @return 订单编号
     */
    public String generateOrderNo() {
        long timestamp = System.currentTimeMillis();
        String dateStr = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")
            .format(java.time.LocalDateTime.now());
        return dateStr + String.format("%08d", nextId() % 100000000);
    }

    /**
     * 等待下一毫秒
     * 
     * @param lastTimestamp 上次时间戳
     * @return 新的时间戳
     */
    private long waitNextMillis(long lastTimestamp) {
        long timestamp = System.currentTimeMillis();
        while (timestamp <= lastTimestamp) {
            timestamp = System.currentTimeMillis();
        }
        return timestamp;
    }

    /**
     * 设置机器 ID
     * 
     * @param machineId 机器 ID（0-1023）
     */
    public void setMachineId(long machineId) {
        if (machineId < 0 || machineId > MAX_MACHINE_NUM) {
            throw new IllegalArgumentException(
                String.format("机器 ID 必须在 0-%d 之间", MAX_MACHINE_NUM)
            );
        }
        this.machineId = machineId;
    }
}
