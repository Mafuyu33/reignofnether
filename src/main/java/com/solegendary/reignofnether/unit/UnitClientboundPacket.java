package com.solegendary.reignofnether.unit;

import com.solegendary.reignofnether.registrars.PacketHandler;
import com.solegendary.reignofnether.resources.ResourceSources;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public class UnitClientboundPacket {

    private final UnitSyncAction syncAction;
    private final int entityId;
    private final float health;
    private final double posX;
    private final double posY;
    private final double posZ;
    private final int food;
    private final int wood;
    private final int ore;
    private final boolean idle;

    public static void sendLeavePacket(LivingEntity entity) {
        PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(),
            new UnitClientboundPacket(UnitSyncAction.LEAVE_LEVEL,
                entity.getId(),0,0,0,0,0,0,0, false)
        );
    }

    public static void sendSyncStatsPacket(LivingEntity entity) {
        boolean isIdle = false;
        if (entity instanceof WorkerUnit worker)
            isIdle = WorkerUnit.isIdle(worker);

        PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(),
                new UnitClientboundPacket(UnitSyncAction.SYNC_STATS,
                        entity.getId(),
                        entity.getHealth(),
                        entity.getX(), entity.getY(), entity.getZ(),
                        0,0,0, isIdle)
        );
    }

    public static void sendSyncResourcesPacket(Unit unit) {
        Resources res = Resources.getTotalResourcesFromItems(unit.getItems());
        PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(),
                new UnitClientboundPacket(UnitSyncAction.SYNC_RESOURCES,
                        ((LivingEntity) unit).getId(), 0,0,0,0,
                        res.food, res.wood, res.ore, false)
        );
    }

    // packet-handler functions
    public UnitClientboundPacket(
        UnitSyncAction syncAction,
        int unitId,
        float health,
        double posX,
        double posY,
        double posZ,
        int food,
        int wood,
        int ore,
        boolean idle
    ) {
        // filter out non-owned entities so we can't control them
        this.syncAction = syncAction;
        this.entityId = unitId;
        this.health = health;
        this.posX = posX;
        this.posY = posY;
        this.posZ = posZ;
        this.food = food;
        this.wood = wood;
        this.ore = ore;
        this.idle = idle;
    }

    public UnitClientboundPacket(FriendlyByteBuf buffer) {
        this.syncAction = buffer.readEnum(UnitSyncAction.class);
        this.entityId = buffer.readInt();
        this.health = buffer.readFloat();
        this.posX = buffer.readDouble();
        this.posY = buffer.readDouble();
        this.posZ = buffer.readDouble();
        this.food = buffer.readInt();
        this.wood = buffer.readInt();
        this.ore = buffer.readInt();
        this.idle = buffer.readBoolean();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeEnum(this.syncAction);
        buffer.writeInt(this.entityId);
        buffer.writeFloat(this.health);
        buffer.writeDouble(this.posX);
        buffer.writeDouble(this.posY);
        buffer.writeDouble(this.posZ);
        buffer.writeInt(this.food);
        buffer.writeInt(this.wood);
        buffer.writeInt(this.ore);
        buffer.writeBoolean(this.idle);
    }

    // client-side packet-consuming functions
    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        final var success = new AtomicBoolean(false);

        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> {
                    switch (this.syncAction) {
                        case LEAVE_LEVEL -> UnitClientEvents.onEntityLeave(this.entityId);
                        case SYNC_STATS -> UnitClientEvents.syncUnitStats(
                                this.entityId,
                                this.health,
                                new Vec3(this.posX, this.posY, this.posZ),
                                this.idle);
                        case SYNC_RESOURCES -> UnitClientEvents.syncUnitResources(
                                this.entityId,
                                new Resources("", this.food, this.wood, this.ore));
                    }
                });
        });
        ctx.get().setPacketHandled(true);
        return success.get();
    }
}
