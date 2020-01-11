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
package org.spongepowered.common.mixin.api.mcp.world;

import net.minecraft.entity.Entity;
import org.spongepowered.api.entity.explosive.Explosive;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;
import org.spongepowered.api.world.explosion.Explosion;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.common.bridge.world.ExplosionBridge;
import org.spongepowered.math.vector.Vector3d;

import java.util.Optional;
import java.util.Random;

import javax.annotation.Nullable;

@Mixin(net.minecraft.world.Explosion.class)
public abstract class ExplosionMixin_API implements Explosion {

    @Shadow @Final private boolean causesFire;
    @Shadow @Final private net.minecraft.world.World world;
    @Shadow @Final private double x;
    @Shadow @Final private double y;
    @Shadow @Final private double z;
    @Shadow @Final private Entity exploder;
    @Shadow @Final private float size;

    @Shadow @Final private net.minecraft.world.Explosion.Mode mode;
    @Nullable private Location api$location;

    @Override
    public Location getLocation() {
        if (this.api$location == null) {
            this.api$location = Location.of((World) this.world, new Vector3d(this.x, this.y, this.z));
        }
        return this.api$location;
    }

    @Override
    public Optional<Explosive> getSourceExplosive() {
        if (this.exploder instanceof Explosive) {
            return Optional.of((Explosive) this.exploder);
        }

        return Optional.empty();
    }

    @Override
    public float getRadius() {
        return this.size;
    }

    @Override
    public boolean canCauseFire() {
        return this.causesFire;
    }

    @Override
    public boolean shouldPlaySmoke() {
        return this.mode != net.minecraft.world.Explosion.Mode.NONE;
    }

    @Override
    public boolean shouldBreakBlocks() {
        return ((ExplosionBridge) this).bridge$getShouldDamageBlocks();
    }

    @Override
    public boolean shouldDamageEntities() {
        return ((ExplosionBridge) this).bridge$getShouldDamageEntities();
    }

    @Override
    public int getResolution() {
        return  ((ExplosionBridge) this).bridge$getResolution();
    }

    @Override
    public float getRandomness() {
        return  ((ExplosionBridge) this).bridge$getRandomness();
    }

    @Override
    public double getKnockback() {
        return  ((ExplosionBridge) this).bridge$getKnockback();
    }

}
