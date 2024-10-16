package yesman.epicfight.world.capabilities.entitypatch.mob;

import java.util.EnumSet;
import java.util.UUID;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.EntityAttributeModificationEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import yesman.epicfight.api.animation.Animator;
import yesman.epicfight.api.animation.JointTransform;
import yesman.epicfight.api.animation.LivingMotions;
import yesman.epicfight.api.animation.Pose;
import yesman.epicfight.api.animation.types.AttackAnimation;
import yesman.epicfight.api.animation.types.DynamicAnimation;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.client.animation.ClientAnimator;
import yesman.epicfight.api.utils.AttackResult;
import yesman.epicfight.api.utils.math.OpenMatrix4f;
import yesman.epicfight.api.utils.math.Vec3f;
import yesman.epicfight.gameasset.Animations;
import yesman.epicfight.gameasset.MobCombatBehaviors;
import yesman.epicfight.network.EpicFightNetworkManager;
import yesman.epicfight.network.server.SPChangeLivingMotion;
import yesman.epicfight.network.server.SPSpawnData;
import yesman.epicfight.particle.EpicFightParticles;
import yesman.epicfight.world.capabilities.entitypatch.Faction;
import yesman.epicfight.world.capabilities.entitypatch.MobPatch;
import yesman.epicfight.world.damagesource.EpicFightDamageSource;
import yesman.epicfight.world.damagesource.StunType;
import yesman.epicfight.world.effect.EpicFightMobEffects;
import yesman.epicfight.world.entity.ai.attribute.EpicFightAttributes;
import yesman.epicfight.world.entity.ai.goal.AnimatedAttackGoal;
import yesman.epicfight.world.entity.ai.goal.CombatBehaviors;
import yesman.epicfight.world.entity.ai.goal.TargetChasingGoal;
import yesman.epicfight.world.gamerule.EpicFightGamerules;

public class EndermanPatch extends MobPatch<EnderMan> {
	private static final UUID SPEED_MODIFIER_RAGE_UUID = UUID.fromString("dc362d1a-8424-11ec-a8a3-0242ac120002");
	private static final AttributeModifier SPEED_MODIFIER_RAGE = new AttributeModifier(SPEED_MODIFIER_RAGE_UUID, "Rage speed bonus", 0.1D, AttributeModifier.Operation.ADDITION);
	
	private boolean onRage;
	private Goal normalAttacks;
	private Goal teleportAttacks;
	private Goal rageAttacks;
	private Goal rageTargeting;
	
	public EndermanPatch() {
		super(Faction.ENDERMAN);
	}
	
	@Override
	public void onJoinWorld(EnderMan enderman, EntityJoinLevelEvent event) {
		if (enderman.level().dimension() == Level.END) {
			if (enderman.level().getGameRules().getBoolean(EpicFightGamerules.NO_MOBS_IN_BOSSFIGHT) && enderman.position().horizontalDistanceSqr() < 40000) {
				event.setCanceled(true);
			}
		}
		
		super.onJoinWorld(enderman, event);
	}
	
	@Override
	public void onStartTracking(ServerPlayer trackingPlayer) {
		if (this.isRaging()) {
			SPSpawnData packet = new SPSpawnData(this.original.getId());
			EpicFightNetworkManager.sendToPlayer(packet, trackingPlayer);
		}
	}
	
	@Override
	public void processSpawnData(ByteBuf buf) {
		ClientAnimator animator = this.getClientAnimator();
		animator.addLivingAnimation(LivingMotions.IDLE, Animations.ENDERMAN_RAGE_IDLE);
		animator.addLivingAnimation(LivingMotions.WALK, Animations.ENDERMAN_RAGE_WALK);
		animator.setCurrentMotionsAsDefault();
	}
	
	public static void initAttributes(EntityAttributeModificationEvent event) {
		event.add(EntityType.ENDERMAN, EpicFightAttributes.STUN_ARMOR.get(), 8.0D);
		event.add(EntityType.ENDERMAN, EpicFightAttributes.IMPACT.get(), 1.8D);
	}
	
	@Override
	protected void initAI() {
		super.initAI();
		this.normalAttacks = new AnimatedAttackGoal<>(this, MobCombatBehaviors.ENDERMAN.build(this));
		this.teleportAttacks = new EndermanTeleportMove(this, MobCombatBehaviors.ENDERMAN_TELEPORT.build(this));
		this.rageAttacks = new AnimatedAttackGoal<>(this, MobCombatBehaviors.ENDERMAN_RAGE.build(this));
		this.rageTargeting = new NearestAttackableTargetGoal<>(this.original, Player.class, true);
		this.original.goalSelector.addGoal(1, new TargetChasingGoal(this, this.getOriginal(), 0.75D, false));
		
		if (this.isRaging()) {
			this.original.targetSelector.addGoal(3, this.rageTargeting);
			this.original.goalSelector.addGoal(1, this.rageAttacks);
		} else {
			this.original.goalSelector.addGoal(1, this.normalAttacks);
			this.original.goalSelector.addGoal(0, this.teleportAttacks);
		}
	}
	
	@Override
	public void initAnimator(Animator animator) {
		animator.addLivingAnimation(LivingMotions.DEATH, Animations.ENDERMAN_DEATH);
		animator.addLivingAnimation(LivingMotions.WALK, Animations.ENDERMAN_WALK);
		animator.addLivingAnimation(LivingMotions.IDLE, Animations.ENDERMAN_IDLE);
	}
	
	@Override
	public void updateMotion(boolean considerInaction) {
		super.commonMobUpdateMotion(considerInaction);
	}
	
	@Override
	public void serverTick(LivingEvent.LivingTickEvent event) {
		super.serverTick(event);
		
		if (this.isRaging() && !this.onRage && this.original.tickCount > 5) {
			this.toRaging();
		} else if (this.onRage && !this.isRaging()) {
			this.toNormal();
		}
	}
	
	@Override
	public void tick(LivingEvent.LivingTickEvent event) {
		if (this.original.getHealth() <= 0.0F) {
			this.original.setXRot(0);
		}
		
		super.tick(event);
	}
	
	@Override
	public void poseTick(DynamicAnimation animation, Pose pose, float elapsedTime, float partialTicks) {
		super.poseTick(animation, pose, elapsedTime, partialTicks);
		
		if (this.isRaging() && pose.getJointTransformData().containsKey("Head_Top")) {
			pose.getOrDefaultTransform("Head_Top").frontResult(JointTransform.getTranslation(new Vec3f(0.0F, 0.25F, 0.0F)), OpenMatrix4f::mul);
		}
	}
	
	@Override
	public AttackResult tryHurt(DamageSource damageSource, float amount) {
		if (!this.original.level().isClientSide()) {
			if (damageSource.getEntity() != null && !this.isRaging()) {
				EpicFightDamageSource extDamageSource = null;
				
				if (damageSource instanceof EpicFightDamageSource) {
					extDamageSource = ((EpicFightDamageSource)damageSource);
				}
				
				if (extDamageSource == null || extDamageSource.getStunType() != StunType.HOLD) {
					int percentage = this.getServerAnimator().getPlayerFor(null).getAnimation() instanceof AttackAnimation ? 10 : 3;
					if (this.original.getRandom().nextInt(percentage) == 0) {
						for (int i = 0; i < 9; i++) {
							if (this.original.teleport()) {
								if (damageSource.getEntity() instanceof LivingEntity) {
									this.original.setLastHurtByMob((LivingEntity) damageSource.getEntity());
								}
								
								if (this.state.inaction()) {
									this.playAnimationSynchronized(Animations.ENDERMAN_TP_EMERGENCE, 0.0F);
								}
								
								return AttackResult.blocked(amount);
							}
						}
					}
				}
			}
		}
		
		return super.tryHurt(damageSource, amount);
	}
	
	public boolean isRaging() {
		return this.original.getHealth() / this.original.getMaxHealth() < 0.33F;
	}
	
	protected void toRaging() {
		this.onRage = true;
		this.playAnimationSynchronized(Animations.ENDERMAN_CONVERT_RAGE, 0);
		
		if (!this.original.isNoAi()) {
			this.original.goalSelector.removeGoal(this.normalAttacks);
			this.original.goalSelector.removeGoal(this.teleportAttacks);
			this.original.goalSelector.addGoal(1, this.rageAttacks);
			this.original.targetSelector.addGoal(3, this.rageTargeting);
			this.original.getEntityData().set(EnderMan.DATA_CREEPY, Boolean.valueOf(true));
			this.original.addEffect(new MobEffectInstance(EpicFightMobEffects.STUN_IMMUNITY.get(), 120000));
			this.original.getAttribute(Attributes.MOVEMENT_SPEED).addTransientModifier(SPEED_MODIFIER_RAGE);
			
			SPChangeLivingMotion msg = new SPChangeLivingMotion(this.original.getId(), true)
					.putPair(LivingMotions.IDLE, Animations.ENDERMAN_RAGE_IDLE)
					.putPair(LivingMotions.WALK, Animations.ENDERMAN_RAGE_WALK);
			EpicFightNetworkManager.sendToAllPlayerTrackingThisEntity(msg, this.original);
		}
	}
	
	protected void toNormal() {
		this.onRage = false;
		
		if (!this.original.isNoAi()) {
			this.original.goalSelector.addGoal(1, this.normalAttacks);
			this.original.goalSelector.addGoal(0, this.teleportAttacks);
			this.original.goalSelector.removeGoal(this.rageAttacks);
			this.original.targetSelector.removeGoal(this.rageTargeting);
			
			if (this.original.getTarget() == null) {
				this.original.getEntityData().set(EnderMan.DATA_CREEPY, Boolean.valueOf(false));
			}
			
			this.original.removeEffect(EpicFightMobEffects.STUN_IMMUNITY.get());
			this.original.getAttribute(Attributes.MOVEMENT_SPEED).removeModifier(SPEED_MODIFIER_RAGE);
			
			SPChangeLivingMotion msg = new SPChangeLivingMotion(this.original.getId(), true)
					.putPair(LivingMotions.IDLE, Animations.ENDERMAN_IDLE)
					.putPair(LivingMotions.WALK, Animations.ENDERMAN_WALK);
			EpicFightNetworkManager.sendToAllPlayerTrackingThisEntity(msg, this.original);
		}
	}
	
	@Override
	public void aboutToDeath() {
		this.original.playSound(SoundEvents.ENDERMAN_TELEPORT, 1.0F, 1.0F);
		
		if (this.isLogicalClient()) {
			for (int i = 0; i < 100; i++) {
				RandomSource rand = original.getRandom();
				Vec3f vec = new Vec3f(rand.nextInt(), rand.nextInt(), rand.nextInt());
				vec.normalise().scale(0.5F);
				Minecraft minecraft = Minecraft.getInstance();
				minecraft.particleEngine.createParticle(EpicFightParticles.ENDERMAN_DEATH_EMIT.get(), this.original.getX(), this.original.getY() + this.original.getDimensions(net.minecraft.world.entity.Pose.STANDING).height / 2, this.original.getZ(), vec.x, vec.y, vec.z);
			}
		}
		
		super.aboutToDeath();
	}
	
	@Override
	public StaticAnimation getHitAnimation(StunType stunType) {
		switch(stunType) {
		case SHORT:
			return Animations.ENDERMAN_HIT_SHORT;
		case LONG:
			return Animations.ENDERMAN_HIT_LONG;
		case HOLD:
			return Animations.ENDERMAN_HIT_SHORT;
		case KNOCKDOWN:
			return Animations.ENDERMAN_NEUTRALIZED;
		case NEUTRALIZE:
			return Animations.ENDERMAN_NEUTRALIZED;
		default:
			return null;
		}
	}
	
	static class EndermanTeleportMove extends AnimatedAttackGoal<EndermanPatch> {
		private int waitingCounter;
		private int delayCounter;
		private CombatBehaviors.Behavior<EndermanPatch> move;
		
		public EndermanTeleportMove(EndermanPatch mobpatch, CombatBehaviors<EndermanPatch> mobAttacks) {
			super(mobpatch, mobAttacks);
			this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}
		
		@Override
		public boolean canUse() {
			this.combatBehaviors.tick();
			
			if (super.canUse()) {
				this.move = this.combatBehaviors.selectRandomBehaviorSeries();
				return this.move != null;
			} else {
				return false;
			}
		}
		
		@Override
		public boolean canContinueToUse() {
			boolean waitExpired = this.waitingCounter <= 100;
			
			if (!waitExpired) {
				this.waitingCounter = 500;
			}
			
	    	return this.checkTargetValid() && !this.mobpatch.getEntityState().hurt() && !this.mobpatch.getEntityState().inaction() && waitExpired;
	    }
		
		@Override
		public void start() {
			this.delayCounter = 20 + this.mobpatch.getOriginal().getRandom().nextInt(5);
			this.waitingCounter = 0;
		}
		
		@Override
		public void stop() {
			this.move = null;
		}
		
		@Override
		public void tick() {
			Mob mob = this.mobpatch.getOriginal();
			LivingEntity target = mob.getTarget();
	        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
	        
			if (this.delayCounter-- < 0 && !this.mobpatch.getEntityState().inaction()) {
				Vec3f vec = new Vec3f((float)(mob.getX() - target.getX()), 0, (float)(mob.getZ() - target.getZ()));
	        	vec.normalise().scale(1.414F);
	        	boolean flag = mob.randomTeleport(target.getX() + vec.x, target.getY(), target.getZ() + vec.z, true);
	        	
				if (flag) {
					this.mobpatch.rotateTo(target, 360.0F, true);
					this.move.execute(this.mobpatch);
		        	mob.level().playSound(null, mob.xo, mob.yo, mob.zo, SoundEvents.ENDERMAN_TELEPORT, mob.getSoundSource(), 1.0F, 1.0F);
		        	mob.playSound(SoundEvents.ENDERMAN_TELEPORT, 1.0F, 1.0F);
		        	this.waitingCounter = 0;
				} else {
	            	this.waitingCounter++;
				}
	        }
	    }
	}
}