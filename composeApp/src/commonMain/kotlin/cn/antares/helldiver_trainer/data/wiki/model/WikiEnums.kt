package cn.antares.helldiver_trainer.data.wiki.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 资料库条目的顶层分类，同时用于对应详情分片（Shard）的按需加载拆分。
 */
@Serializable
enum class WikiKind(val labelZh: String) {
    @SerialName("stratagem")
    STRATAGEM("战备"),

    @SerialName("weapon")
    WEAPON("武器"),

    @SerialName("enemy")
    ENEMY("敌人"),
}

/**
 * 战备指令的四个基本输入方向。
 */
@Serializable
enum class WikiDirection {
    UP,
    RIGHT,
    DOWN,
    LEFT,
}

/**
 * 战备所属的战术大类，用于目录筛选与二级过滤。
 */
@Serializable
enum class StratagemCategory(val labelZh: String) {
    @SerialName("eagle")
    EAGLE("飞鹰"),

    @SerialName("orbital")
    ORBITAL("轨道"),

    @SerialName("support_weapon")
    SUPPORT_WEAPON("支援武器"),

    @SerialName("backpack")
    BACKPACK("背包"),

    @SerialName("vehicle")
    VEHICLE("载具"),

    @SerialName("defensive")
    DEFENSIVE("防守"),

    @SerialName("mission")
    MISSION("任务"),
}

/**
 * 武器子类枚举，涵盖主武器、副武器及投掷物各细分类别。
 */
@Serializable
enum class WeaponClass(val labelZh: String) {
    @SerialName("assault_rifle")
    ASSAULT_RIFLE("突击步枪"),

    @SerialName("marksman_rifle")
    MARKSMAN_RIFLE("射手步枪"),

    @SerialName("submachine_gun")
    SUBMACHINE_GUN("冲锋枪"),

    @SerialName("shotgun")
    SHOTGUN("霰弹枪"),

    @SerialName("explosive")
    EXPLOSIVE("爆炸武器"),

    @SerialName("energy_based")
    ENERGY_BASED("能量武器"),

    @SerialName("pistol")
    PISTOL("手枪"),

    @SerialName("melee")
    MELEE("近战"),

    @SerialName("standard")
    STANDARD("标准"),

    @SerialName("special")
    SPECIAL("特殊"),
}

/**
 * 武器装备槽位，并通过 [allowedClasses] 约束各槽位下允许联动的武器子类。
 */
@Serializable
enum class WeaponSlot(val labelZh: String) {
    @SerialName("primary")
    PRIMARY("主武器"),

    @SerialName("secondary")
    SECONDARY("副武器"),

    @SerialName("throwable")
    THROWABLE("投掷物");

    /**
     * 返回当前装备槽位下合法的二级武器子类列表，用于筛选栏的槽位-子类二级联动。
     */
    fun allowedClasses(): List<WeaponClass> = when (this) {
        PRIMARY -> listOf(
            WeaponClass.ASSAULT_RIFLE,
            WeaponClass.MARKSMAN_RIFLE,
            WeaponClass.SUBMACHINE_GUN,
            WeaponClass.SHOTGUN,
            WeaponClass.EXPLOSIVE,
            WeaponClass.ENERGY_BASED,
            WeaponClass.SPECIAL,
        )

        SECONDARY -> listOf(
            WeaponClass.PISTOL,
            WeaponClass.MELEE,
            WeaponClass.SPECIAL,
        )

        THROWABLE -> listOf(
            WeaponClass.STANDARD,
            WeaponClass.SPECIAL,
        )
    }
}

/**
 * 敌人所属阵营。
 */
@Serializable
enum class EnemyFaction(val labelZh: String) {
    @SerialName("terminid")
    TERMINID("终结族"),

    @SerialName("automaton")
    AUTOMATON("机器人"),

    @SerialName("illuminate")
    ILLUMINATE("光能者"),
}

/**
 * 敌人体型等级，用于筛选与威胁分级展示。
 */
@Serializable
enum class EnemySizeClass(val labelZh: String) {
    @SerialName("small")
    SMALL("小型"),

    @SerialName("medium")
    MEDIUM("中型"),

    @SerialName("heavy")
    HEAVY("重型"),

    @SerialName("massive")
    MASSIVE("巨型"),
}

/**
 * 资料条目间的有向关联类型，正反向语义在仓库层统一映射为中文标签。
 */
@Serializable
enum class WikiRelationType {
    @SerialName("calls_in")
    CALLS_IN,

    @SerialName("variant_of")
    VARIANT_OF,
}

/**
 * 数值事实字段的四态可用性标记，区分真实未知、不适用与无限制，避免与 0 或 null 混淆。
 */
@Serializable
enum class WikiFactStatus {
    @SerialName("known")
    KNOWN,

    @SerialName("unknown")
    UNKNOWN,

    @SerialName("not_applicable")
    NOT_APPLICABLE,

    @SerialName("unlimited")
    UNLIMITED,
}
