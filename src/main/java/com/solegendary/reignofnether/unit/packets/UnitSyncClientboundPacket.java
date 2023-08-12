package com.solegendary.reignofnether.unit.packets;

import com.solegendary.reignofnether.registrars.PacketHandler;
import com.solegendary.reignofnether.resources.ResourceName;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.UnitSyncAction;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public class UnitSyncClientboundPacket {

    private final UnitSyncAction syncAction;
    private final int entityId;
    private final float health;
    private final double posX;
    private final double posY;
    private final double posZ;
    private final int food;
    private final int wood;
    private final int ore;
    private final String ownerName;

    public static void sendLeavePacket(LivingEntity entity) {
        PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(),
            new UnitSyncClientboundPacket(UnitSyncAction.LEAVE_LEVEL,
                entity.getId(),0,0,0,0,0,0,0, "")
        );
    }

    public static void sendSyncStatsPacket(LivingEntity entity) {
        boolean isBuilding = false;
        ResourceName gatherTarget = ResourceName.NONE;

        String owner = "";
        if (entity instanceof Unit unit)
            owner = unit.getOwnerName();

        PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(),
            new UnitSyncClientboundPacket(UnitSyncAction.SYNC_STATS,
                entity.getId(),
                entity.getHealth(),
                entity.getX(), entity.getY(), entity.getZ(),
                0,0,0, owner)
        );
    }

    public static void sendSyncResourcesPacket(Unit unit) {
        Resources res = Resources.getTotalResourcesFromItems(unit.getItems());
        PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(),
            new UnitSyncClientboundPacket(UnitSyncAction.SYNC_RESOURCES,
                ((LivingEntity) unit).getId(), 0,0,0,0,
                res.food, res.wood, res.ore, "")
        );
    }

    public static void sendSyncEvokerCastingPacket(LivingEntity entity, boolean startCasting) {
        PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(),
            new UnitSyncClientboundPacket(
                startCasting ? UnitSyncAction.EVOKER_START_CASTING : UnitSyncAction.EVOKER_STOP_CASTING,
                entity.getId(),
                0,0,0,0,0,0,0, "")
        );
    }

    // packet-handler functions
    public UnitSyncClientboundPacket(
        UnitSyncAction syncAction,
        int unitId,
        float health,
        double posX,
        double posY,
        double posZ,
        int food,
        int wood,
        int ore,
        String ownerName
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
        this.ownerName = ownerName;
    }

    public UnitSyncClientboundPacket(FriendlyByteBuf buffer) {
        this.syncAction = buffer.readEnum(UnitSyncAction.class);
        this.entityId = buffer.readInt();
        this.health = buffer.readFloat();
        this.posX = buffer.readDouble();
        this.posY = buffer.readDouble();
        this.posZ = buffer.readDouble();
        this.food = buffer.readInt();
        this.wood = buffer.readInt();
        this.ore = buffer.readInt();
        this.ownerName = buffer.readUtf();
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
        buffer.writeUtf(this.ownerName);
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
                                this.ownerName);
                        case SYNC_RESOURCES -> UnitClientEvents.syncUnitResources(
                                this.entityId,
                                new Resources("", this.food, this.wood, this.ore));
                        case EVOKER_START_CASTING -> UnitClientEvents.syncEvokerCasting(this.entityId, true);
                        case EVOKER_STOP_CASTING -> UnitClientEvents.syncEvokerCasting(this.entityId, false);
                    }
                });
        });
        ctx.get().setPacketHandled(true);
        return success.get();
    }
}