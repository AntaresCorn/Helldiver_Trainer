package cn.antares.helldiver_trainer.util

import cn.antares.helldiver_trainer.MR
import cn.antares.helldiver_trainer.viewmodel.GameViewModel.StratagemInput.DOWN
import cn.antares.helldiver_trainer.viewmodel.GameViewModel.StratagemInput.LEFT
import cn.antares.helldiver_trainer.viewmodel.GameViewModel.StratagemInput.RIGHT
import cn.antares.helldiver_trainer.viewmodel.GameViewModel.StratagemInput.UP
import cn.antares.helldiver_trainer.viewmodel.GameViewModel.StratagemItem

object StratagemInitializer {

    fun initStratagems(): List<StratagemItem> {
        return listOf(
            /**
             * 飞鹰
             */
            StratagemItem(
                id = "eagle_1",
                name = "“飞鹰”110MM火箭巢",
                icon = MR.images.eagle_110mm_rocket_pods,
                inputs = listOf(UP, RIGHT, UP, LEFT),
            ),
            StratagemItem(
                id = "eagle_2",
                name = "“飞鹰”500KG炸弹",
                icon = MR.images.eagle_500kg_bomb,
                inputs = listOf(UP, RIGHT, DOWN, DOWN, DOWN),
            ),
            StratagemItem(
                id = "eagle_3",
                name = "“飞鹰”空袭",
                icon = MR.images.eagle_airstrike,
                inputs = listOf(UP, RIGHT, DOWN, RIGHT),
            ),
            StratagemItem(
                id = "eagle_4",
                name = "“飞鹰”集束炸弹",
                icon = MR.images.eagle_cluster_bomb,
                inputs = listOf(UP, RIGHT, DOWN, DOWN, RIGHT),
            ),
            StratagemItem(
                id = "eagle_5",
                name = "“飞鹰”凝固汽油弹空袭",
                icon = MR.images.eagle_napalm_airstrike,
                inputs = listOf(UP, RIGHT, DOWN, UP),
            ),
            StratagemItem(
                id = "eagle_6",
                name = "“飞鹰”烟雾攻击",
                icon = MR.images.eagle_smoke_strike,
                inputs = listOf(UP, RIGHT, UP, DOWN),
            ),
            StratagemItem(
                id = "eagle_7",
                name = "“飞鹰”机枪扫射",
                icon = MR.images.eagle_strafing_run,
                inputs = listOf(UP, RIGHT, RIGHT),
            ),

            /**
             * 轨道
             */
            StratagemItem(
                id = "orbital_1",
                name = "轨道120MM高爆弹火力网",
                icon = MR.images.orbital_120mm_he_barrage,
                inputs = listOf(RIGHT, RIGHT, DOWN, LEFT, RIGHT, DOWN),
            ),
            StratagemItem(
                id = "orbital_2",
                name = "轨道380MM高爆弹火力网",
                icon = MR.images.orbital_380mm_he_barrage,
                inputs = listOf(RIGHT, DOWN, UP, UP, LEFT, DOWN, DOWN),
            ),
            StratagemItem(
                id = "orbital_3",
                name = "轨道空爆攻击",
                icon = MR.images.orbital_airburst_strike,
                inputs = listOf(RIGHT, RIGHT, RIGHT),
            ),
            StratagemItem(
                id = "orbital_4",
                name = "轨道电磁冲击波攻击",
                icon = MR.images.orbital_ems_strike,
                inputs = listOf(RIGHT, RIGHT, LEFT, DOWN),
            ),
            StratagemItem(
                id = "orbital_5",
                name = "轨道毒气攻击",
                icon = MR.images.orbital_gas_strike,
                inputs = listOf(RIGHT, RIGHT, DOWN, RIGHT),
            ),
            StratagemItem(
                id = "orbital_6",
                name = "轨道加特林火力网",
                icon = MR.images.orbital_gatling_barrage,
                inputs = listOf(RIGHT, DOWN, LEFT, UP, UP),
            ),
            StratagemItem(
                id = "orbital_7",
                name = "轨道激光炮",
                icon = MR.images.orbital_laser,
                inputs = listOf(RIGHT, DOWN, UP, RIGHT, DOWN),
            ),
            StratagemItem(
                id = "orbital_8",
                name = "轨道凝固汽油弹火力网",
                icon = MR.images.orbital_napalm_barrage,
                inputs = listOf(RIGHT, RIGHT, DOWN, LEFT, RIGHT, UP),
            ),
            StratagemItem(
                id = "orbital_9",
                name = "轨道精准攻击",
                icon = MR.images.orbital_precision_strike,
                inputs = listOf(RIGHT, RIGHT, UP),
            ),
            StratagemItem(
                id = "orbital_10",
                name = "轨道炮攻击",
                icon = MR.images.orbital_railcannon_strike,
                inputs = listOf(RIGHT, UP, DOWN, DOWN, RIGHT),
            ),
            StratagemItem(
                id = "orbital_11",
                name = "轨道烟雾攻击",
                icon = MR.images.orbital_smoke_strike,
                inputs = listOf(RIGHT, RIGHT, DOWN, UP),
            ),
            StratagemItem(
                id = "orbital_12",
                name = "轨道游走火力网",
                icon = MR.images.orbital_walking_barrage,
                inputs = listOf(RIGHT, DOWN, RIGHT, DOWN, RIGHT, DOWN),
            ),

            /**
             * 支援武器
             */
            StratagemItem(
                id = "sw_1",
                name = "空爆火箭弹发射器",
                icon = MR.images.sw_airburst_rocket_launcher,
                inputs = listOf(DOWN, UP, UP, LEFT, RIGHT),
            ),
            StratagemItem(
                id = "sw_2",
                name = "反器材步枪",
                icon = MR.images.sw_anti_materiel_rifle,
                inputs = listOf(DOWN, LEFT, RIGHT, UP, DOWN),
            ),
            StratagemItem(
                id = "sw_3",
                name = "电弧发射器",
                icon = MR.images.sw_arc_thrower,
                inputs = listOf(DOWN, RIGHT, DOWN, UP, LEFT, LEFT),
            ),
            StratagemItem(
                id = "sw_4",
                name = "机炮",
                icon = MR.images.sw_autocannon,
                inputs = listOf(DOWN, LEFT, DOWN, UP, UP, RIGHT),
            ),
            StratagemItem(
                id = "sw_5",
                name = "突击兵",
                icon = MR.images.sw_commando,
                inputs = listOf(DOWN, LEFT, UP, DOWN, RIGHT),
            ),
            StratagemItem(
                id = "sw_6",
                name = "空爆火箭弹发射器",
                icon = MR.images.sw_airburst_rocket_launcher,
                inputs = listOf(DOWN, UP, UP, LEFT, RIGHT),
            ),
            StratagemItem(
                id = "sw_7",
                name = "纪元",
                icon = MR.images.sw_epoch,
                inputs = listOf(DOWN, LEFT, UP, LEFT, RIGHT),
            ),
            StratagemItem(
                id = "sw_8",
                name = "消耗性反坦克武器",
                icon = MR.images.sw_expendable_anti_tank,
                inputs = listOf(DOWN, DOWN, LEFT, UP, RIGHT),
            ),
            StratagemItem(
                id = "sw_9",
                name = "火焰喷射器",
                icon = MR.images.sw_flamethrower,
                inputs = listOf(DOWN, LEFT, UP, DOWN, UP),
            ),
            StratagemItem(
                id = "sw_10",
                name = "缓和使者",
                icon = MR.images.sw_gl_52_de_escalator,
                inputs = listOf(DOWN, RIGHT, UP, LEFT, RIGHT),
            ),
            StratagemItem(
                id = "sw_11",
                name = "榴弹发射器",
                icon = MR.images.sw_grenade_launcher,
                inputs = listOf(DOWN, LEFT, UP, LEFT, DOWN),
            ),
            StratagemItem(
                id = "sw_12",
                name = "重机枪",
                icon = MR.images.sw_heavy_machine_gun,
                inputs = listOf(DOWN, LEFT, UP, DOWN, DOWN),
            ),
            StratagemItem(
                id = "sw_13",
                name = "激光大炮",
                icon = MR.images.sw_laser_cannon,
                inputs = listOf(DOWN, LEFT, DOWN, UP, LEFT),
            ),
            StratagemItem(
                id = "sw_14",
                name = "机枪",
                icon = MR.images.sw_machine_gun,
                inputs = listOf(DOWN, LEFT, DOWN, UP, RIGHT),
            ),
            StratagemItem(
                id = "sw_15",
                name = "唯一真旗",
                icon = MR.images.sw_one_true_flag,
                inputs = listOf(DOWN, LEFT, RIGHT, RIGHT, UP),
            ),
            StratagemItem(
                id = "sw_16",
                name = "类星体加农炮",
                icon = MR.images.sw_quasar_cannon,
                inputs = listOf(DOWN, DOWN, UP, LEFT, RIGHT),
            ),
            StratagemItem(
                id = "sw_17",
                name = "磁轨炮",
                icon = MR.images.sw_railgun,
                inputs = listOf(DOWN, RIGHT, DOWN, UP, LEFT, RIGHT),
            ),
            StratagemItem(
                id = "sw_18",
                name = "无后坐力炮",
                icon = MR.images.sw_recoilless_rifle,
                inputs = listOf(DOWN, LEFT, RIGHT, RIGHT, LEFT),
            ),
            StratagemItem(
                id = "sw_19",
                name = "飞矛",
                icon = MR.images.sw_spear,
                inputs = listOf(DOWN, DOWN, UP, DOWN, DOWN),
            ),
            StratagemItem(
                id = "sw_20",
                name = "StA-X3 W.A.S.P.发射器",
                icon = MR.images.sw_sta_x3_wasp_launcher,
                inputs = listOf(DOWN, DOWN, UP, DOWN, RIGHT),
            ),
            StratagemItem(
                id = "sw_21",
                name = "盟友",
                icon = MR.images.sw_stalwart,
                inputs = listOf(DOWN, LEFT, DOWN, UP, UP, LEFT),
            ),
            StratagemItem(
                id = "sw_22",
                name = "灭菌器",
                icon = MR.images.sw_sterilizer,
                inputs = listOf(DOWN, LEFT, UP, DOWN, LEFT),
            ),
            StratagemItem(
                id = "sw_23",
                name = "矛枪",
                icon = MR.images.sw_speargun,
                inputs = listOf(DOWN, RIGHT, DOWN, LEFT, UP, RIGHT),
            ),
            StratagemItem(
                id = "sw_24",
                name = "消耗性凝固汽油弹",
                icon = MR.images.sw_expendable_napalm,
                inputs = listOf(DOWN, DOWN, LEFT, UP, LEFT),
            ),

            /**
             * 背包
             */
            StratagemItem(
                id = "bp_1",
                name = "防弹护盾背包",
                icon = MR.images.bp_ballistic_shield_backpack,
                inputs = listOf(DOWN, LEFT, DOWN, DOWN, UP, LEFT),
            ),
            StratagemItem(
                id = "bp_2",
                name = "定向护盾",
                icon = MR.images.bp_directional_shield,
                inputs = listOf(DOWN, UP, LEFT, RIGHT, UP, UP),
            ),
            StratagemItem(
                id = "bp_3",
                name = "“护卫犬”",
                icon = MR.images.bp_guard_dog,
                inputs = listOf(DOWN, UP, LEFT, UP, RIGHT, DOWN),
            ),
            StratagemItem(
                id = "bp_4",
                name = "“护卫犬”腐息",
                icon = MR.images.bp_guard_dog_breath,
                inputs = listOf(DOWN, UP, LEFT, UP, RIGHT, UP),
            ),
            StratagemItem(
                id = "bp_5",
                name = "“护卫犬”K-9",
                icon = MR.images.bp_guard_dog_k_9,
                inputs = listOf(DOWN, UP, LEFT, UP, RIGHT, LEFT),
            ),
            StratagemItem(
                id = "bp_6",
                name = "“护卫犬”漫游车",
                icon = MR.images.bp_guard_dog_rover,
                inputs = listOf(DOWN, UP, LEFT, UP, RIGHT, RIGHT),
            ),
            StratagemItem(
                id = "bp_7",
                name = "便携式地狱火炸弹",
                icon = MR.images.bp_hellbomb_portable,
                inputs = listOf(DOWN, RIGHT, UP, UP, UP),
            ),
            StratagemItem(
                id = "bp_8",
                name = "悬浮背包",
                icon = MR.images.bp_hover_pack,
                inputs = listOf(DOWN, UP, UP, DOWN, LEFT, RIGHT),
            ),
            StratagemItem(
                id = "bp_9",
                name = "喷射背包",
                icon = MR.images.bp_jump_pack,
                inputs = listOf(DOWN, UP, UP, DOWN, UP),
            ),
            StratagemItem(
                id = "bp_10",
                name = "防护罩生成包",
                icon = MR.images.bp_shield_generator_pack,
                inputs = listOf(DOWN, UP, LEFT, RIGHT, LEFT, RIGHT),
            ),
            StratagemItem(
                id = "bp_11",
                name = "补给背包",
                icon = MR.images.bp_supply_pack,
                inputs = listOf(DOWN, LEFT, DOWN, UP, UP, DOWN),
            ),
            StratagemItem(
                id = "bp_12",
                name = "传送背包",
                icon = MR.images.bp_warp_pack,
                inputs = listOf(DOWN, LEFT, RIGHT, DOWN, LEFT, RIGHT),
            ),

            /**
             * 载具
             */
            StratagemItem(
                id = "v_1",
                name = "快速侦察载具",
                icon = MR.images.v_fast_recon_vehicle,
                inputs = listOf(LEFT, DOWN, RIGHT, DOWN, LEFT, DOWN, UP),
            ),
            StratagemItem(
                id = "v_2",
                name = "“解放者”外骨骼装甲",
                icon = MR.images.v_emancipator_exosuit,
                inputs = listOf(LEFT, DOWN, RIGHT, UP, LEFT, DOWN, UP),
            ),
            StratagemItem(
                id = "v_3",
                name = "“爱国者”外骨骼装甲",
                icon = MR.images.v_patriot_exosuit,
                inputs = listOf(LEFT, DOWN, RIGHT, UP, LEFT, DOWN, DOWN),
            ),

            /**
             * 防守战备
             */
            StratagemItem(
                id = "ds_1",
                name = "反步兵雷区",
                icon = MR.images.ds_anti_personnel_minefield,
                inputs = listOf(DOWN, LEFT, UP, RIGHT),
            ),
            StratagemItem(
                id = "ds_2",
                name = "反坦克炮台",
                icon = MR.images.ds_anti_tank_emplacement,
                inputs = listOf(DOWN, UP, LEFT, RIGHT, RIGHT, RIGHT),
            ),
            StratagemItem(
                id = "ds_3",
                name = "反坦克地雷",
                icon = MR.images.ds_anti_tank_mines,
                inputs = listOf(DOWN, LEFT, UP, UP),
            ),
            StratagemItem(
                id = "ds_4",
                name = "自动哨戒炮",
                icon = MR.images.ds_autocannon_sentry,
                inputs = listOf(DOWN, UP, RIGHT, UP, LEFT, UP),
            ),
            StratagemItem(
                id = "ds_5",
                name = "电磁冲击波迫击哨戒炮",
                icon = MR.images.ds_ems_mortar_sentry,
                inputs = listOf(DOWN, UP, RIGHT, DOWN, UP),
            ),
            StratagemItem(
                id = "ds_6",
                name = "火焰喷射哨戒炮",
                icon = MR.images.ds_flame_sentry,
                inputs = listOf(DOWN, UP, RIGHT, DOWN, UP, UP),
            ),
            StratagemItem(
                id = "ds_7",
                name = "毒气地雷",
                icon = MR.images.ds_gas_mine,
                inputs = listOf(DOWN, LEFT, LEFT, RIGHT),
            ),
            StratagemItem(
                id = "ds_8",
                name = "加特林哨戒炮",
                icon = MR.images.ds_gatling_sentry,
                inputs = listOf(DOWN, UP, RIGHT, LEFT),
            ),
            StratagemItem(
                id = "ds_9",
                name = "掷弹兵防卫墙",
                icon = MR.images.ds_grenadier_battlement,
                inputs = listOf(DOWN, RIGHT, DOWN, LEFT, RIGHT),
            ),
            StratagemItem(
                id = "ds_10",
                name = "重机枪部署支架",
                icon = MR.images.ds_hmg_emplacement,
                inputs = listOf(DOWN, UP, LEFT, RIGHT, RIGHT, LEFT),
            ),
            StratagemItem(
                id = "ds_11",
                name = "燃烧地雷",
                icon = MR.images.ds_incendiary_mines,
                inputs = listOf(DOWN, LEFT, LEFT, DOWN),
            ),
            StratagemItem(
                id = "ds_12",
                name = "激光哨戒炮",
                icon = MR.images.ds_laser_sentry,
                inputs = listOf(DOWN, UP, RIGHT, DOWN, UP, RIGHT),
            ),
            StratagemItem(
                id = "ds_13",
                name = "哨戒机枪",
                icon = MR.images.ds_machine_gun_sentry,
                inputs = listOf(DOWN, UP, RIGHT, RIGHT, UP),
            ),
            StratagemItem(
                id = "ds_14",
                name = "迫击哨戒炮",
                icon = MR.images.ds_mortar_sentry,
                inputs = listOf(DOWN, UP, RIGHT, RIGHT, DOWN),
            ),
            StratagemItem(
                id = "ds_15",
                name = "火箭哨戒炮",
                icon = MR.images.ds_rocket_sentry,
                inputs = listOf(DOWN, UP, RIGHT, RIGHT, LEFT),
            ),
            StratagemItem(
                id = "ds_16",
                name = "防护罩生成中继器",
                icon = MR.images.ds_shield_generator_relay,
                inputs = listOf(DOWN, DOWN, LEFT, RIGHT, LEFT, RIGHT),
            ),
            StratagemItem(
                id = "ds_17",
                name = "特斯拉塔",
                icon = MR.images.ds_tesla_tower,
                inputs = listOf(DOWN, UP, RIGHT, UP, LEFT, RIGHT),
            ),
            StratagemItem(
                id = "ds_18",
                name = "单兵导弹发射井",
                icon = MR.images.ds_solo_silo,
                inputs = listOf(DOWN, UP, RIGHT, DOWN, DOWN),
            ),

            /**
             * 任务战备
             */
            StratagemItem(
                id = "ms_1",
                name = "暗液体容器",
                icon = MR.images.ms_dark_fluid_vessel,
                inputs = listOf(UP, LEFT, RIGHT, DOWN, UP, UP),
            ),
            StratagemItem(
                id = "ms_2",
                name = "重新武装“飞鹰”",
                icon = MR.images.ms_eagle_rearm,
                inputs = listOf(UP, UP, LEFT, UP, RIGHT),
            ),
            StratagemItem(
                id = "ms_3",
                name = "地狱火炸弹",
                icon = MR.images.ms_hellbomb,
                inputs = listOf(DOWN, UP, LEFT, DOWN, UP, RIGHT, DOWN, UP),
            ),
            StratagemItem(
                id = "ms_4",
                name = "“虫窝破裂者”钻机",
                icon = MR.images.ms_hive_breaker_drill,
                inputs = listOf(LEFT, UP, DOWN, RIGHT, DOWN, DOWN),
            ),
            /*StratagemItem(
                id = 78,
                name = "轨道照明曳光弹",
                icon = MR.images.ms_orbital_illumination_flare,
                inputs = listOf(RIGHT, RIGHT, LEFT, LEFT),
            ),*/
            StratagemItem(
                id = "ms_5",
                name = "勘探钻机",
                icon = MR.images.ms_prospecting_drill,
                inputs = listOf(DOWN, DOWN, LEFT, RIGHT, DOWN, DOWN),
            ),
            StratagemItem(
                id = "ms_6",
                name = "增援",
                icon = MR.images.ms_reinforce,
                inputs = listOf(UP, DOWN, RIGHT, LEFT, UP),
            ),
            StratagemItem(
                id = "ms_7",
                name = "重新补给",
                icon = MR.images.ms_resupply,
                inputs = listOf(DOWN, DOWN, UP, RIGHT),
            ),
            StratagemItem(
                id = "ms_8",
                name = "超级地球武装部队大炮",
                icon = MR.images.ms_seaf_artillery,
                inputs = listOf(RIGHT, UP, UP, DOWN),
            ),
            StratagemItem(
                id = "ms_9",
                name = "地震探测仪",
                icon = MR.images.ms_seismic_probe,
                inputs = listOf(UP, UP, LEFT, RIGHT, DOWN, DOWN),
            ),
            StratagemItem(
                id = "ms_10",
                name = "S.O.S.求救信标",
                icon = MR.images.ms_sos_beacon,
                inputs = listOf(UP, DOWN, RIGHT, UP),
            ),
            StratagemItem(
                id = "ms_11",
                name = "超级地球旗帜",
                icon = MR.images.ms_super_earth_flag,
                inputs = listOf(DOWN, UP, DOWN, UP),
            ),
            StratagemItem(
                id = "ms_12",
                name = "地壳钻机",
                icon = MR.images.ms_tectonic_drill,
                inputs = listOf(UP, DOWN, UP, DOWN, UP, DOWN),
            ),
            StratagemItem(
                id = "ms_13",
                name = "上传数据",
                icon = MR.images.ms_upload_data,
                inputs = listOf(LEFT, RIGHT, UP, UP, UP),
            ),
            StratagemItem(
                id = "ms_14",
                name = "运送超级固态硬盘",
                icon = MR.images.ms_sssd_delivery,
                inputs = listOf(DOWN, DOWN, DOWN, UP, UP),
            ),
            StratagemItem(
                id = "ms_15",
                name = "启动钻机",
                icon = MR.images.ms_711_drill,
                inputs = listOf(DOWN, DOWN, LEFT, LEFT, DOWN, DOWN),
            ),
        )
    }
}