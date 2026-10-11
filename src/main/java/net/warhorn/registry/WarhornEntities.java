package net.warhorn.registry;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.warhorn.Warhorn;
import net.warhorn.entity.BatteringRamEntity;
import net.warhorn.entity.PowderKegEntity;
import net.warhorn.entity.CatapultEntity;
import net.warhorn.entity.FirePotEntity;
import net.warhorn.entity.KidnapperEntity;
import net.warhorn.entity.SapperEntity;
import net.warhorn.entity.WarBannerEntity;
import net.warhorn.entity.WarlordAxeEntity;
import net.warhorn.entity.WarlordEntity;

public final class WarhornEntities {
	private static final ResourceKey<EntityType<?>> WARLORD_KEY = ResourceKey.create(Registries.ENTITY_TYPE, Warhorn.id("warlord"));

	public static final EntityType<WarlordEntity> WARLORD = Registry.register(BuiltInRegistries.ENTITY_TYPE, WARLORD_KEY,
		EntityType.Builder.of(WarlordEntity::new, MobCategory.MONSTER)
			.sized(0.6f, 1.95f)
			.clientTrackingRange(16)
			.build(WARLORD_KEY));

	private static final ResourceKey<EntityType<?>> AXE_KEY = ResourceKey.create(Registries.ENTITY_TYPE, Warhorn.id("warlord_axe"));
	private static final ResourceKey<EntityType<?>> BANNER_KEY = ResourceKey.create(Registries.ENTITY_TYPE, Warhorn.id("war_banner"));

	public static final EntityType<WarlordAxeEntity> WARLORD_AXE = Registry.register(BuiltInRegistries.ENTITY_TYPE, AXE_KEY,
		EntityType.Builder.<WarlordAxeEntity>of(WarlordAxeEntity::new, MobCategory.MISC)
			.sized(1.2f, 1.2f)
			.clientTrackingRange(10)
			.updateInterval(1)
			.noSave()
			.build(AXE_KEY));

	public static final EntityType<WarBannerEntity> WAR_BANNER = Registry.register(BuiltInRegistries.ENTITY_TYPE, BANNER_KEY,
		EntityType.Builder.<WarBannerEntity>of(WarBannerEntity::new, MobCategory.MISC)
			.sized(0.9f, 3.4f)
			.clientTrackingRange(16)
			.updateInterval(20)
			.fireImmune()
			.build(BANNER_KEY));

	private static final ResourceKey<EntityType<?>> SAPPER_KEY = ResourceKey.create(Registries.ENTITY_TYPE, Warhorn.id("sapper"));
	private static final ResourceKey<EntityType<?>> KEG_KEY = ResourceKey.create(Registries.ENTITY_TYPE, Warhorn.id("powder_keg"));

	public static final EntityType<SapperEntity> SAPPER = Registry.register(BuiltInRegistries.ENTITY_TYPE, SAPPER_KEY,
		EntityType.Builder.of(SapperEntity::new, MobCategory.MONSTER)
			.sized(0.6f, 1.95f)
			.clientTrackingRange(12)
			.build(SAPPER_KEY));

	public static final EntityType<PowderKegEntity> POWDER_KEG = Registry.register(BuiltInRegistries.ENTITY_TYPE, KEG_KEY,
		EntityType.Builder.<PowderKegEntity>of(PowderKegEntity::new, MobCategory.MISC)
			.sized(0.75f, 0.95f)
			.clientTrackingRange(10)
			.updateInterval(2)
			.build(KEG_KEY));

	private static final ResourceKey<EntityType<?>> RAM_KEY = ResourceKey.create(Registries.ENTITY_TYPE, Warhorn.id("battering_ram"));

	public static final EntityType<BatteringRamEntity> BATTERING_RAM = Registry.register(BuiltInRegistries.ENTITY_TYPE, RAM_KEY,
		EntityType.Builder.<BatteringRamEntity>of(BatteringRamEntity::new, MobCategory.MISC)
			.sized(1.0f, 1.0f)
			.clientTrackingRange(12)
			.updateInterval(1)
			.build(RAM_KEY));

	private static final ResourceKey<EntityType<?>> KIDNAPPER_KEY = ResourceKey.create(Registries.ENTITY_TYPE, Warhorn.id("kidnapper"));

	public static final EntityType<KidnapperEntity> KIDNAPPER = Registry.register(BuiltInRegistries.ENTITY_TYPE, KIDNAPPER_KEY,
		EntityType.Builder.of(KidnapperEntity::new, MobCategory.MONSTER)
			.sized(0.6f, 1.95f)
			.clientTrackingRange(12)
			.build(KIDNAPPER_KEY));

	private static final ResourceKey<EntityType<?>> CATAPULT_KEY = ResourceKey.create(Registries.ENTITY_TYPE, Warhorn.id("catapult"));
	private static final ResourceKey<EntityType<?>> FIRE_POT_KEY = ResourceKey.create(Registries.ENTITY_TYPE, Warhorn.id("fire_pot"));

	public static final EntityType<CatapultEntity> CATAPULT = Registry.register(BuiltInRegistries.ENTITY_TYPE, CATAPULT_KEY,
		EntityType.Builder.<CatapultEntity>of(CatapultEntity::new, MobCategory.MISC)
			.sized(2.6f, 2.6f)
			.clientTrackingRange(12)
			.updateInterval(5)
			.build(CATAPULT_KEY));

	public static final EntityType<FirePotEntity> FIRE_POT = Registry.register(BuiltInRegistries.ENTITY_TYPE, FIRE_POT_KEY,
		EntityType.Builder.<FirePotEntity>of(FirePotEntity::new, MobCategory.MISC)
			.sized(0.4f, 0.4f)
			.clientTrackingRange(16)
			.updateInterval(1)
			.fireImmune()
			.build(FIRE_POT_KEY));

	private WarhornEntities() {
	}

	public static void register() {
		FabricDefaultAttributeRegistry.register(WARLORD, WarlordEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(SAPPER, SapperEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(KIDNAPPER, KidnapperEntity.createAttributes());
	}
}
