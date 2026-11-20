package cn.edu.sdu.java.server.repositorys;

import cn.edu.sdu.java.server.models.PetRoom;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 按平台账户读取和保存桌宠进度 */
public interface PetRoomRepository extends JpaRepository<PetRoom, Integer> {
    // 排行和个人档案共用计分口径，消费分只读取宠物用品的实际扣款
    String PURCHASE_PREFIX = "pet-food:";
    // 只累计猫粮实际扣款，不把退款或其他消费计入成长
    String SPENDING = "select user_id, sum(-amount) as spent from blockcoin_entry "
        + "where amount < 0 and entry_id like '" + PURCHASE_PREFIX + "%' group by user_id";
    // 合并昵称、互动和实付支出，作为公开排名的计分来源
    String SCORES = "select room.user_id, "
        + "names.nickname, "
        + "1 + room.experience + coalesce(care.care_points, 0) as interaction_points, "
        + "coalesce(purchases.spent, 0) as spent from pet_room room "
        + "left join pet_nickname names on names.user_id = room.user_id "
        + "left join pet_care care on care.user_id = room.user_id "
        + "left join (" + SPENDING + ") purchases on purchases.user_id = room.user_id";

    /** 读取宠物用品的历史实际支出，重试请求不产生第二笔流水 */
    @Query(value = "select coalesce(sum(-amount), 0) from blockcoin_entry "
        + "where user_id = :userId and amount < 0 and entry_id like '" + PURCHASE_PREFIX + "%'", nativeQuery = true)
    long spending(@Param("userId") int userId);

    /** 读取当前宠物的自定义昵称，未改名时返回空结果 */
    @Query(value = "select nickname from pet_nickname where user_id = :userId", nativeQuery = true)
    Optional<String> readNickname(@Param("userId") int userId);

    /** 首次改名时建立昵称记录，再次改名时只替换名字 */
    @Modifying
    @Query(value = "insert into pet_nickname (user_id, nickname) values (:userId, :nickname) "
        + "on duplicate key update nickname = :nickname", nativeQuery = true)
    void saveNickname(@Param("userId") int userId, @Param("nickname") String nickname);

    /** 提取榜首和自己的名次，同分并列，仅用编号稳定展示顺序 */
    @Query(value = "select user_id as ownerId, nickname, score, rank_number as rankNumber "
        + "from (select user_id, nickname, interaction_points + spent as score, "
        + "rank() over (order by interaction_points + spent desc) as rank_number, "
        + "row_number() over (order by interaction_points + spent desc, user_id) as row_position "
        + "from (" + SCORES + ") scores) ranked "
        + "where row_position <= :limit or user_id = :ownerId order by row_position", nativeQuery = true)
    List<RankRow> leaderboard(@Param("ownerId") int ownerId, @Param("limit") int limit);

    /** 数据库投影只读取排行所需字段，账户编号仅用于在服务器识别自己 */
    interface RankRow {
        /** 返回内部账户编号 */
        Integer getOwnerId();

        /** 返回宠物昵称，未改名时由服务填入默认值 */
        String getNickname();

        /** 返回服务器计算的总成长值 */
        Long getScore();

        /** 返回并列规则下的名次 */
        Long getRankNumber();
    }
}
