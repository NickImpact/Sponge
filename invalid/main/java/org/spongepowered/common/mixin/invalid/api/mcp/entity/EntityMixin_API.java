/*
 * This file is part of Sponge, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered <https://www.spongepowered.org>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.spongepowered.common.mixin.invalid.api.mcp.entity;

import static com.google.common.base.Preconditions.checkNotNull;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntitySize;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.play.server.SPlayerPositionLookPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.server.ServerWorld;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.data.Keys;
import org.spongepowered.api.entity.EntityArchetype;
import org.spongepowered.api.entity.EntitySnapshot;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.CauseStackManager;
import org.spongepowered.api.event.cause.EventContextKeys;
import org.spongepowered.api.event.cause.entity.teleport.TeleportTypes;
import org.spongepowered.api.event.entity.MoveEntityEvent;
import org.spongepowered.api.text.translation.Translation;
import org.spongepowered.api.util.AABB;
import org.spongepowered.api.util.RelativePositions;
import org.spongepowered.api.util.Transform;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Implements;
import org.spongepowered.asm.mixin.Interface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.common.SpongeImpl;
import org.spongepowered.common.SpongeImplHooks;
import org.spongepowered.common.bridge.data.VanishableBridge;
import org.spongepowered.common.bridge.network.ServerPlayNetHandlerBridge;
import org.spongepowered.common.bridge.world.ServerWorldBridge;
import org.spongepowered.common.bridge.world.TeleporterBridge;
import org.spongepowered.common.bridge.world.WorldBridge;
import org.spongepowered.common.bridge.world.chunk.ServerChunkProviderBridge;
import org.spongepowered.common.entity.EntityUtil;
import org.spongepowered.common.event.tracking.PhaseTracker;
import org.spongepowered.common.event.tracking.phase.plugin.BasicPluginContext;
import org.spongepowered.common.event.tracking.phase.plugin.PluginPhase;
import org.spongepowered.common.util.VecHelper;
import org.spongepowered.math.vector.Vector3d;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

@Mixin(Entity.class)
@Implements(@Interface(iface = org.spongepowered.api.entity.Entity.class, prefix = "entity$"))
public abstract class EntityMixin_API implements org.spongepowered.api.entity.Entity {

    // @formatter:off

    @Shadow public net.minecraft.world.World world;
    @Shadow public double posX;
    @Shadow public double posY;
    @Shadow public double posZ;
    @Shadow public Vec3d motion;
    @Shadow public float rotationYaw;
    @Shadow public float rotationPitch;
    @Shadow public boolean removed;
    @Shadow private EntitySize size;
    @Shadow protected Random rand;
    @Shadow public int ticksExisted;
    @Shadow public int fire;
    @Shadow public DimensionType dimension;
    @Shadow protected UUID entityUniqueID;
    @Shadow @Final private net.minecraft.entity.EntityType<?> type;

    @Shadow public abstract void shadow$setPosition(double x, double y, double z);
    @Shadow public abstract void shadow$remove();
    @Shadow public abstract int shadow$getAir();
    @Shadow public abstract void shadow$setAir(int air);
    @Shadow public abstract UUID shadow$getUniqueID();
    @Shadow public abstract void shadow$setFire(int seconds);
    @Shadow public abstract boolean shadow$attackEntityFrom(DamageSource source, float amount);
    @Shadow public abstract int shadow$getEntityId();
    @Shadow public abstract void shadow$playSound(SoundEvent soundIn, float volume, float pitch);
    @Shadow protected abstract void shadow$setRotation(float yaw, float pitch);
    @Shadow protected abstract AxisAlignedBB shadow$getBoundingBox();
    @Shadow @Nullable public abstract MinecraftServer shadow$getServer();
    @Shadow public abstract boolean shadow$writeUnlessRemoved(CompoundNBT compound);

    // @formatter:on

    @Override
    public EntitySnapshot createSnapshot() {
        throw new UnsupportedOperationException("implement me");
    }

    @SuppressWarnings({"ConstantConditions", "RedundantCast"})
    @Override
    public boolean setLocation(Location location) {
        checkNotNull(location, "The location was null!");
        if (this.isRemoved()) {
            return false;
        }

        if (!SpongeImpl.getWorldManager().isDimensionTypeRegistered(((ServerWorld) location.getWorld()).getDimension().getType())) {
            return false;
        }

        try (final BasicPluginContext context = PluginPhase.State.TELEPORT.createPhaseContext(PhaseTracker.SERVER)) {
            context.buildAndSwitch();

            final MoveEntityEvent.Teleport event;

            try (final CauseStackManager.StackFrame frame = Sponge.getCauseStackManager().pushCauseFrame()) {
                if (!frame.getCurrentContext().containsKey(EventContextKeys.TELEPORT_TYPE)) {
                    frame.addContext(EventContextKeys.TELEPORT_TYPE, TeleportTypes.PLUGIN);
                }

                event = EntityUtil.handleDisplaceEntityTeleportEvent((Entity) (Object) this, location);
                if (event.isCancelled()) {
                    return false;
                }

                location = Location.of(event.getToWorld(), event.getToTransform().getPosition());
                this.rotationPitch = (float) event.getToTransform().getPitch();
                this.rotationYaw = (float) event.getToTransform().getYaw();
            }

            final ServerChunkProviderBridge chunkProviderServer = (ServerChunkProviderBridge) ((ServerWorld) this.world).getChunkProvider();
            final boolean previous = chunkProviderServer.bridge$getForceChunkRequests();
            chunkProviderServer.bridge$setForceChunkRequests(true);
            try {
                final List<Entity> passengers = ((Entity) (Object) this).getPassengers();

                boolean isTeleporting = true;
                boolean isChangingDimension = false;
                if (location.getWorld().getProperties().getUniqueId() != ((World) this.world).getProperties().getUniqueId()) {
                    if ((Entity) (Object) this instanceof ServerPlayerEntity) {
                        // Close open containers
                        final ServerPlayerEntity entityPlayerMP = (ServerPlayerEntity) (Object) this;
                        if (entityPlayerMP.openContainer != entityPlayerMP.container) {
                            ((Player) entityPlayerMP).closeInventory(); // Call API method to make sure we capture it
                        }

                        EntityUtil.transferPlayerToWorld(entityPlayerMP, event, (ServerWorld) location.getWorld(),
                                (TeleporterBridge) ((ServerWorld) location.getWorld()).getDefaultTeleporter());
                    } else {
                        EntityUtil.transferEntityToWorld((Entity) (Object) this, event, (ServerWorld) location.getWorld(),
                                (TeleporterBridge) ((ServerWorld) location.getWorld()).getDefaultTeleporter(), false);
                    }

                    isChangingDimension = true;
                }

                final double distance = location.getPosition().distance(this.getPosition());

                if (distance <= 4) {
                    isTeleporting = false;
                }

                if ((Entity) (Object) this instanceof ServerPlayerEntity && ((ServerPlayerEntity) (Entity) (Object) this).connection != null) {
                    final ServerPlayerEntity player = (ServerPlayerEntity) (Entity) (Object) this;

                    // No reason to attempt to load chunks unless we're teleporting
                    if (isTeleporting || isChangingDimension) {
                        // Close open containers
                        if (player.openContainer != player.container) {
                            ((Player) player).closeInventory(); // Call API method to make sure we capture it
                        }

                        // TODO - determine if this is right.
                        ((ServerWorld) location.getWorld()).getChunkProvider()
                                .forceChunk(new ChunkPos(location.getChunkPosition().getX(), location.getChunkPosition().getZ()), true);
                    }
                    player.connection
                            .setPlayerLocation(location.getX(), location.getY(), location.getZ(), ((Entity) (Object) this).rotationYaw,
                                    ((Entity) (Object) this).rotationPitch);
                    ((ServerPlayNetHandlerBridge) player.connection).bridge$setLastMoveLocation(null); // Set last move to teleport target
                } else {
                    this.shadow$setPosition(location.getPosition().getX(), location.getPosition().getY(), location.getPosition().getZ());
                }

                if (isTeleporting || isChangingDimension) {
                    // Re-attach passengers
                    for (final Entity passenger : passengers) {
                        if (((World) passenger.getEntityWorld()).getProperties().getUniqueId() != ((World) this.world).getProperties().getUniqueId()) {
                            ((org.spongepowered.api.entity.Entity) passenger).setLocation(location);
                        }
                        passenger.startRiding((Entity) (Object) this, true);
                    }
                }
                return true;
            } finally {
                chunkProviderServer.bridge$setForceChunkRequests(previous);
            }

        }
    }

    @SuppressWarnings({"RedundantCast", "ConstantConditions"})
    @Override
    public boolean setLocationAndRotation(final Location location, final Vector3d rotation, final EnumSet<RelativePositions> relativePositions) {
        boolean relocated = true;

        if (relativePositions.isEmpty()) {
            // This is just a normal teleport that happens to set both.
            relocated = this.setLocation(location);
            this.setRotation(rotation);
        } else {
            if (((Entity) (Object) this) instanceof ServerPlayerEntity && ((ServerPlayerEntity) (Entity) (Object) this).connection != null) {
                // Players use different logic, as they support real relative movement.
                final EnumSet<SPlayerPositionLookPacket.Flags> relativeFlags = EnumSet.noneOf(SPlayerPositionLookPacket.Flags.class);

                if (relativePositions.contains(RelativePositions.X)) {
                    relativeFlags.add(SPlayerPositionLookPacket.Flags.X);
                }

                if (relativePositions.contains(RelativePositions.Y)) {
                    relativeFlags.add(SPlayerPositionLookPacket.Flags.Y);
                }

                if (relativePositions.contains(RelativePositions.Z)) {
                    relativeFlags.add(SPlayerPositionLookPacket.Flags.Z);
                }

                if (relativePositions.contains(RelativePositions.PITCH)) {
                    relativeFlags.add(SPlayerPositionLookPacket.Flags.X_ROT);
                }

                if (relativePositions.contains(RelativePositions.YAW)) {
                    relativeFlags.add(SPlayerPositionLookPacket.Flags.Y_ROT);
                }

                ((ServerPlayerEntity) ((Entity) (Object) this)).connection.setPlayerLocation(location.getPosition().getX(), location.getPosition()
                        .getY(), location.getPosition().getZ(), (float) rotation.getY(), (float) rotation.getX(), relativeFlags);
            } else {
                Location resultantLocation = this.getLocation();
                Vector3d resultantRotation = this.getRotation();

                if (relativePositions.contains(RelativePositions.X)) {
                    resultantLocation = resultantLocation.add(location.getPosition().getX(), 0, 0);
                }

                if (relativePositions.contains(RelativePositions.Y)) {
                    resultantLocation = resultantLocation.add(0, location.getPosition().getY(), 0);
                }

                if (relativePositions.contains(RelativePositions.Z)) {
                    resultantLocation = resultantLocation.add(0, 0, location.getPosition().getZ());
                }

                if (relativePositions.contains(RelativePositions.PITCH)) {
                    resultantRotation = resultantRotation.add(rotation.getX(), 0, 0);
                }

                if (relativePositions.contains(RelativePositions.YAW)) {
                    resultantRotation = resultantRotation.add(0, rotation.getY(), 0);
                }

                // From here just a normal teleport is needed.
                relocated = this.setLocation(resultantLocation);
                this.setRotation(resultantRotation);
            }
        }
        return relocated;
    }

    @Override
    public Vector3d getScale() {
        return Vector3d.ONE;
    }

    @Override
    public void setScale(final Vector3d scale) {
        // do nothing, Minecraft doesn't properly support this yet
    }

    @Override
    public Transform getTransform() {
        return Transform.of(this.getPosition(), this.getRotation(), this.getScale());
    }

    @Override
    public boolean setTransform(final Transform transform) {
        checkNotNull(transform, "The transform cannot be null!");
        final Vector3d position = transform.getPosition();

        this.shadow$setPosition(position.getX(), position.getY(), position.getZ());
        this.setRotation(transform.getRotation());
        this.setScale(transform.getScale());
        if (!((WorldBridge) this.world).bridge$isFake() && SpongeImplHooks.onServerThread()) {
            ((ServerWorld) this.world).chunkCheck((Entity) (Object) this);
        }

        return false;
    }

    @Override
    public boolean transferToWorld(final World world, final Vector3d position) {
        checkNotNull(world, "World was null!");
        checkNotNull(position, "Position was null!");
        return this.setLocation(Location.of(world, position));
    }

    @Override
    public Vector3d getRotation() {
        return new Vector3d(this.rotationPitch, this.rotationYaw, 0);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public void setRotation(final Vector3d rotation) {
        checkNotNull(rotation, "Rotation was null!");
        if (this.isRemoved()) {
            return;
        }
        if (((Entity) (Object) this) instanceof ServerPlayerEntity && ((ServerPlayerEntity) (Entity) (Object) this).connection != null) {
            // Force an update, this also set the rotation in this entity
            ((ServerPlayerEntity) (Entity) (Object) this).connection.setPlayerLocation(this.getPosition().getX(), this.getPosition().getY(),
                    this.getPosition().getZ(), (float) rotation.getY(), (float) rotation.getX(), (Set) EnumSet.noneOf(RelativePositions.class));
        } else {
            if (!this.world.isRemote) { // We can't set the rotation update on client worlds.
                ((ServerWorldBridge) this.getWorld()).bridge$addEntityRotationUpdate((Entity) (Object) this, rotation);
            }

            // Let the entity tracker do its job, this just updates the variables
            this.shadow$setRotation((float) rotation.getY(), (float) rotation.getX());
        }
    }

    @Override
    public Optional<AABB> getBoundingBox() {
        final AxisAlignedBB boundingBox = this.shadow$getBoundingBox();
        if (boundingBox == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(VecHelper.toSpongeAABB(boundingBox));
        } catch (final IllegalArgumentException exception) {
            // Bounding box is degenerate, the entity doesn't actually have one
            return Optional.empty();
        }
    }

    @Override
    public Translation getTranslation() {
        return this.getType().getTranslation();
    }

    @Override
    public boolean canSee(final org.spongepowered.api.entity.Entity entity) {
        // note: this implementation will be changing with contextual data
        final Optional<Boolean> optional = entity.get(Keys.VANISH);
        return (!optional.isPresent() || !optional.get()) && !((VanishableBridge) entity).bridge$isVanished();
    }

    @Override
    public EntityArchetype createArchetype() {
        return new SpongeEntityArchetypeBuilder().from(this).build();
    }

}