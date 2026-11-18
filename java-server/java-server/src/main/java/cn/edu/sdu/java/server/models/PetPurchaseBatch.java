package cn.edu.sdu.java.server.models;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 保存同一账户尚未发送站内通知的小屋消费，关闭页面也不会丢失 */
@Entity
@Table(name = "pet_purchase_batch")
public class PetPurchaseBatch {
    @Id
    private Integer userId;
    private String batchId;
    private long spent;
    private long quantity;

    /** 留给持久化框架读取数据库记录 */
    protected PetPurchaseBatch() {
    }

    /** 为指定账户创建一个尚未结算通知的批次 */
    public PetPurchaseBatch(Integer userId, String batchId) {
        this.userId = userId;
        this.batchId = batchId;
    }

    /** 将一份已购买的猫粮计入通知，price 是实际支付的字块币 */
    public void addFood(long price) {
        spent = spent + price;
        quantity = quantity + 1;
    }

    /** 返回消息使用的唯一批次编号 */
    public String getBatchId() {
        return batchId;
    }

    /** 返回尚未通知的支出总额 */
    public long getSpent() {
        return spent;
    }

    /** 返回尚未通知的购买份数 */
    public long getQuantity() {
        return quantity;
    }
}
