package dev.starcore.starcore.nation.permission;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 国家权限测试
 * 确保权限系统的正确性和完整性
 */
class NationPermissionTest {

    @Nested
    @DisplayName("权限枚举测试")
    class PermissionEnumTests {

        @Test
        @DisplayName("所有权限枚举应有定义")
        void allPermissionsShouldHaveDefinition() {
            for (NationPermission permission : NationPermission.values()) {
                assertNotNull(permission.name());
                assertFalse(permission.name().isEmpty());
            }
        }

        @Test
        @DisplayName("权限应可按名称查找")
        void permissionsShouldBeFindableByName() {
            for (NationPermission permission : NationPermission.values()) {
                NationPermission found = NationPermission.valueOf(permission.name());
                assertEquals(permission, found);
            }
        }

        @Test
        @DisplayName("每个权限应有Bukkit节点")
        void eachPermissionShouldHaveBukkitNode() {
            for (NationPermission permission : NationPermission.values()) {
                String node = permission.getBukkitNode();
                assertNotNull(node);
                assertTrue(node.startsWith("starcore.nation."));
            }
        }

        @Test
        @DisplayName("每个权限应有权限级别")
        void eachPermissionShouldHaveLevel() {
            for (NationPermission permission : NationPermission.values()) {
                PermissionLevel level = permission.getDefaultLevel();
                assertNotNull(level);
            }
        }

        @Test
        @DisplayName("每个权限应有描述")
        void eachPermissionShouldHaveDescription() {
            for (NationPermission permission : NationPermission.values()) {
                String desc = permission.getDescription();
                assertNotNull(desc);
                assertFalse(desc.isEmpty());
            }
        }
    }

    @Nested
    @DisplayName("权限配置键测试")
    class ConfigKeyTests {

        @Test
        @DisplayName("getConfigKey应返回正确格式")
        void getConfigKeyShouldReturnCorrectFormat() {
            assertEquals("city.create", NationPermission.CITY_CREATE.getConfigKey());
            assertEquals("war.declare", NationPermission.WAR_DECLARE.getConfigKey());
        }

        @Test
        @DisplayName("fromConfigKey应正确解析")
        void fromConfigKeyShouldParseCorrectly() {
            assertEquals(NationPermission.CITY_CREATE, NationPermission.fromConfigKey("city.create"));
            assertEquals(NationPermission.WAR_DECLARE, NationPermission.fromConfigKey("war.declare"));
        }

        @Test
        @DisplayName("无效配置键应返回null")
        void invalidConfigKeyShouldReturnNull() {
            assertNull(NationPermission.fromConfigKey("invalid.key"));
            assertNull(NationPermission.fromConfigKey(""));
        }
    }

    @Nested
    @DisplayName("权限分类测试")
    class PermissionCategoryTests {

        @Test
        @DisplayName("城市管理权限数量正确")
        void cityPermissionsCountCorrect() {
            NationPermission[] perms = NationPermission.getCityPermissions();
            assertEquals(5, perms.length);
        }

        @Test
        @DisplayName("领地管理权限数量正确")
        void territoryPermissionsCountCorrect() {
            NationPermission[] perms = NationPermission.getTerritoryPermissions();
            assertEquals(6, perms.length);
        }

        @Test
        @DisplayName("成员管理权限数量正确")
        void memberPermissionsCountCorrect() {
            NationPermission[] perms = NationPermission.getMemberPermissions();
            assertEquals(6, perms.length);
        }

        @Test
        @DisplayName("外交权限数量正确")
        void diplomacyPermissionsCountCorrect() {
            NationPermission[] perms = NationPermission.getDiplomacyPermissions();
            assertEquals(4, perms.length);
        }

        @Test
        @DisplayName("战争权限数量正确")
        void warPermissionsCountCorrect() {
            NationPermission[] perms = NationPermission.getWarPermissions();
            assertEquals(5, perms.length);
        }

        @Test
        @DisplayName("经济权限数量正确")
        void economyPermissionsCountCorrect() {
            NationPermission[] perms = NationPermission.getEconomyPermissions();
            assertEquals(5, perms.length);
        }

        @Test
        @DisplayName("军事权限数量正确")
        void militaryPermissionsCountCorrect() {
            NationPermission[] perms = NationPermission.getMilitaryPermissions();
            assertEquals(4, perms.length);
        }
    }

    @Nested
    @DisplayName("权限级别测试")
    class PermissionLevelTests {

        @Test
        @DisplayName("创始人权限应为FOUNDER级别")
        void founderPermissionsShouldBeFounderLevel() {
            assertEquals(PermissionLevel.FOUNDER, NationPermission.NATION_DISBAND.getDefaultLevel());
            assertEquals(PermissionLevel.FOUNDER, NationPermission.NATION_TRANSFER.getDefaultLevel());
        }

        @Test
        @DisplayName("领导人权限应为LEADER级别")
        void leaderPermissionsShouldBeLeaderLevel() {
            assertEquals(PermissionLevel.LEADER, NationPermission.WAR_DECLARE.getDefaultLevel());
            assertEquals(PermissionLevel.LEADER, NationPermission.MEMBER_KICK.getDefaultLevel());
        }

        @Test
        @DisplayName("受信任成员权限应为TRUSTED级别")
        void trustedPermissionsShouldBeTrustedLevel() {
            assertEquals(PermissionLevel.TRUSTED, NationPermission.TERRITORY_CLAIM.getDefaultLevel());
            assertEquals(PermissionLevel.TRUSTED, NationPermission.ARMY_COMMAND.getDefaultLevel());
        }

        @Test
        @DisplayName("普通成员权限应为MEMBER级别")
        void memberPermissionsShouldBeMemberLevel() {
            assertEquals(PermissionLevel.MEMBER, NationPermission.BANK_DEPOSIT.getDefaultLevel());
            assertEquals(PermissionLevel.MEMBER, NationPermission.SPAWN_TP.getDefaultLevel());
        }
    }

    @Nested
    @DisplayName("关键权限测试")
    class CriticalPermissionTests {

        @Test
        @DisplayName("解散国家权限仅限创始人")
        void disbandNationShouldBeFounderOnly() {
            assertEquals(PermissionLevel.FOUNDER, NationPermission.NATION_DISBAND.getDefaultLevel());
        }

        @Test
        @DisplayName("宣战权限仅限领导人")
        void declareWarShouldBeLeaderOnly() {
            assertEquals(PermissionLevel.LEADER, NationPermission.WAR_DECLARE.getDefaultLevel());
        }

        @Test
        @DisplayName("存款权限对所有成员开放")
        void depositShouldBeOpenToMembers() {
            assertEquals(PermissionLevel.MEMBER, NationPermission.BANK_DEPOSIT.getDefaultLevel());
        }

        @Test
        @DisplayName("取款权限仅限领导人")
        void withdrawShouldBeLeaderOnly() {
            assertEquals(PermissionLevel.LEADER, NationPermission.BANK_WITHDRAW.getDefaultLevel());
        }
    }

    @Nested
    @DisplayName("权限节点格式测试")
    class BukkitNodeTests {

        @Test
        @DisplayName("Bukkit节点格式应正确")
        void bukkitNodesShouldHaveCorrectFormat() {
            assertEquals("starcore.nation.city.create", NationPermission.CITY_CREATE.getBukkitNode());
            assertEquals("starcore.nation.war.declare", NationPermission.WAR_DECLARE.getBukkitNode());
            assertEquals("starcore.nation.bank.withdraw", NationPermission.BANK_WITHDRAW.getBukkitNode());
        }

        @Test
        @DisplayName("所有节点应以starcore开头")
        void allNodesShouldStartWithStarcore() {
            for (NationPermission permission : NationPermission.values()) {
                assertTrue(permission.getBukkitNode().startsWith("starcore."),
                    "Permission " + permission + " should start with starcore.");
            }
        }
    }

    @Nested
    @DisplayName("权限统计测试")
    class StatisticsTests {

        @Test
        @DisplayName("总权限数量应符合预期")
        void totalPermissionCountShouldMatch() {
            int total = NationPermission.values().length;
            // 根据代码注释，总权限约 55+ 种
            assertTrue(total > 50, "Should have more than 50 permissions, found: " + total);
        }
    }
}
