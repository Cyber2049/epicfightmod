package yesman.epicfight.world.capabilities.entitypatch.mob;

import java.util.Optional;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraftforge.event.entity.EntityAttributeModificationEvent;
import yesman.epicfight.api.animation.Animator;
import yesman.epicfight.api.animation.LivingMotions;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.gameasset.Animations;
import yesman.epicfight.gameasset.EpicFightSounds;
import yesman.epicfight.gameasset.MobCombatBehaviors;
import yesman.epicfight.world.capabilities.entitypatch.MobPatch;
import yesman.epicfight.world.damagesource.StunType;
import yesman.epicfight.world.entity.ai.attribute.EpicFightAttributes;
import yesman.epicfight.world.entity.ai.behavior.AnimatedCombatBehavior;
import yesman.epicfight.world.entity.ai.behavior.MoveToTargetSinkStopInaction;
import yesman.epicfight.world.entity.ai.brain.BrainRecomposer;

public class HoglinPatch extends MobPatch<Hoglin> {
	@Override
	public void initAI() {
		super.initAI();
		BrainRecomposer.recomposeHoglinBrain(this.original.getBrain(), new AnimatedCombatBehavior<>(this, MobCombatBehaviors.HOGLIN.build(this)), new MoveToTargetSinkStopInaction());
	}
	
	@Override
	public void initAnimator(Animator animator) {
		animator.addLivingAnimation(LivingMotions.IDLE, Animations.HOGLIN_IDLE);
		animator.addLivingAnimation(LivingMotions.WALK, Animations.HOGLIN_WALK);
		animator.addLivingAnimation(LivingMotions.DEATH, Animations.HOGLIN_DEATH);
	}
	
	public static void initAttributes(EntityAttributeModificationEvent event) {
		event.add(EntityType.HOGLIN, EpicFightAttributes.MAX_STRIKES.get(), 4.0D);
		event.add(EntityType.HOGLIN, EpicFightAttributes.IMPACT.get(), 5.0D);
	}
	
	@Override
	public void updateMotion(boolean considerInaction) {
		super.commonMobUpdateMotion(considerInaction);
	}
	
	@Override
	public StaticAnimation getHitAnimation(StunType stunType) {
		return null;
	}
	
	@Override
	public SoundEvent getWeaponHitSound(InteractionHand hand) {
		return this.original.isBaby() ? EpicFightSounds.BLUNT_HIT.get() : EpicFightSounds.BLUNT_HIT_HARD.get();
	}
	
	@Override
	public SoundEvent getSwingSound(InteractionHand hand) {
		return this.original.isBaby() ? EpicFightSounds.WHOOSH.get() : EpicFightSounds.WHOOSH_BIG.get();
	}
	
	@Override
	public LivingEntity getTarget() {
		Optional<LivingEntity> opt = this.original.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET);
		return opt.orElse(null);
	}
}