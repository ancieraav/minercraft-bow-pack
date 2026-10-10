package dev.bowpack.gametest;

import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/** Observes actual server effect packets without changing their delivery. */
final class BlockEffectRecorder implements AutoCloseable {
    private final List<ClientboundLevelEventPacket> events = new ArrayList<>();
    private final ServerPlayer player;
    private final ServerGamePacketListenerImpl original;
    private final EmbeddedChannel channel;

    BlockEffectRecorder(GameTestHelper test) {
        player = BookAccessGameTests.player(test);
        original = player.connection;
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        channel = new EmbeddedChannel(connection);
        player.connection = new ServerGamePacketListenerImpl(test.getLevel().getServer(), connection, player,
                CommonListenerCookie.createInitial(original.getOwner(), false)) {
            @Override
            public void send(Packet<?> packet) {
                if (packet instanceof ClientboundLevelEventPacket event) events.add(event);
                original.send(packet);
            }
        };
    }

    long breaksAt(BlockPos position) {
        return events.stream().filter(event -> event.getType() == 2001 && event.getPos().equals(position)).count();
    }

    void clear() {
        events.clear();
    }

    @Override
    public void close() {
        player.connection = original;
        player.discard();
        channel.finishAndReleaseAll();
    }
}
