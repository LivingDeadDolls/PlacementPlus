package placementplus;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import placementplus.mixin.ClientLevelAccessor;

/**
 * The rotation the server has for the player while clicks that need another one are sent.
 *
 * <p>Pistons, observers, dispensers and the like face by the head's rotation, not the body's. The server sets
 * the body's rotation as soon as a rotation packet arrives, but the head only follows when it next ticks the
 * player, after handling that tick's packets. So a click that needs the head turned sideways is held back until
 * the server has ticked the player since the rotation arrived.
 *
 * <p>To know when that has happened, a "use item" packet is sent right after the rotation, for a hand whose use does
 * nothing (empty, or holding a plain block). The server only acknowledges its sequence number, and it sends those
 * acknowledgements at the start of each player tick, just before turning the head. Once the acknowledgement is
 * back, the head has turned, however far behind the server is running. When neither hand is safe to use, it
 * waits {@link #WAIT_TICKS} client ticks instead, which a server running behind can outpace.
 *
 * <p>While the server is turned, every click goes through here, including those with the player's own
 * rotation, so none is sent with a rotation left over from another. At the end of a tick with nothing waiting,
 * the player's own rotation is sent back, and once the head has followed it, vanilla is left in charge again.
 * Meanwhile {@link #rotationToSend} keeps vanilla's movement packets from sending another rotation in between.
 */
public final class ServerLook {
    /** Client ticks to wait for the head when no hand is free; two make it likely a server tick passed. */
    private static final int WAIT_TICKS = 2;
    /** If no acknowledgement comes within this many client ticks of the turn, the head is taken to have followed. */
    private static final int GIVE_UP_TICKS = 40;

    private static int ticks;
    /** The rotation last sent to the server, or null when vanilla is in charge and the head matches the player. */
    private static Look sent;
    private static int sentAt;
    /** Without a free hand: the tick from which the server's head is expected to match {@link #sent}. */
    private static int readyAt;
    /** With a free hand: the sequence number whose acknowledgement shows the head matches {@link #sent}, or -1. */
    private static int readySequence = -1;
    private static int acknowledged = -1;
    /** The level acknowledgements are counted for; weak, so it does not keep a closed level in memory. */
    private static WeakReference<ClientLevel> probeLevel = new WeakReference<>(null);
    /** The server head's yaw before the last turn, while it may not have followed yet. */
    private static float headBefore;
    private static final Deque<Held> heldBack = new ArrayDeque<>();

    /** A click waiting for the server to be turned to {@code turn}. */
    private record Held(FastPlacement.Click click, Look turn) {
    }

    private ServerLook() {
    }

    /** Whether the server has a rotation from here rather than from vanilla. */
    static boolean isTurned() {
        return sent != null;
    }

    /** Whether a click looking {@code look} must wait for the server to turn the player's head first. */
    static boolean mustWait(LocalPlayer player, Look look) {
        if (look.equals(sent)) {
            return !headFollowed();
        }
        return !look.facesLike(look.withYRot(headYaw(player)));
    }

    /**
     * Keeps {@code click} to send in order once the server is turned to {@code turn}, and turns it for the first
     * held-back click. {@code turn} is the click's own rotation, or the player's when it has none.
     */
    static void holdBack(ClientPacketListener connection, LocalPlayer player, FastPlacement.Click click, Look turn) {
        heldBack.addLast(new Held(click, turn));
        turn(connection, player, heldBack.peekFirst().turn());
    }

    /** Sends {@code look} to the server, unless it is already turned that way. */
    static void turn(ClientPacketListener connection, LocalPlayer player, Look look) {
        if (look.equals(sent)) {
            return;
        }
        connection.send(new ServerboundMovePlayerPacket.Rot(look.yRot(), look.xRot(), player.onGround(), player.horizontalCollision));
        if (sent == null || !look.facesLike(look.withYRot(sent.yRot()))) {
            // A new yaw: the head has to get there from wherever it is now. Otherwise the head is already on its
            // way to (or at) this yaw, and the wait stays as it was.
            float head = headYaw(player);
            headBefore = head;
            sentAt = ticks;
            readySequence = -1;
            readyAt = ticks;
            if (!look.facesLike(look.withYRot(head))) {
                readySequence = sendProbe(connection, player, look);
                readyAt = ticks + WAIT_TICKS;
            }
        }
        sent = look;
    }

    /** Called with each block change acknowledgement the server sends, for the level it numbers them in. */
    public static void onAcknowledged(ClientLevel level, int sequence) {
        if (level == probeLevel.get()) {
            acknowledged = Math.max(acknowledged, sequence);
        }
    }

    /**
     * The rotation vanilla's movement packets should carry: the one sent from here while the server is turned,
     * so they do not undo it, or null for the player's own.
     */
    public static Look rotationToSend() {
        return sent;
    }

    /**
     * Called at the end of every client tick: sends the held-back clicks whose time has come, then turns the
     * server back to the player's own rotation once nothing is waiting.
     */
    public static void endTick(Minecraft minecraft, Runnable startUseItem) {
        ticks++;
        LocalPlayer player = minecraft.player;
        ClientPacketListener connection = minecraft.getConnection();
        if (player == null || connection == null) {
            heldBack.clear();
            sent = null;
            readySequence = -1;
            acknowledged = -1;
            return;
        }
        if (minecraft.gui.screen() != null) {
            // Not now: a screen took over.
            heldBack.clear();
        }
        while (!heldBack.isEmpty()) {
            Held held = heldBack.peekFirst();
            if (mustWait(player, held.turn())) {
                turn(connection, player, held.turn());
                return;
            }
            heldBack.removeFirst();
            FastPlacement.place(minecraft, held.click(), startUseItem);
        }
        if (sent == null) {
            return;
        }
        if (!Look.of(player).facesLike(sent)) {
            turn(connection, player, Look.of(player));
        } else if (headFollowed()) {
            // The head is back with the player's own yaw. Send the player's exact rotation once more, since
            // vanilla only sends it when it changes, and leave the rest to vanilla.
            if (!sent.equals(Look.of(player))) {
                turn(connection, player, Look.of(player));
            }
            sent = null;
        }
    }

    /** Whether the server's head has followed the last turn. */
    private static boolean headFollowed() {
        if (ticks - sentAt >= GIVE_UP_TICKS) {
            // Never acknowledged (a server that drops the packet?): place anyway rather than not at all.
            return true;
        }
        return readySequence >= 0 ? acknowledged >= readySequence : ticks >= readyAt;
    }

    /** The yaw the server's head has now, as far as the client can tell. */
    private static float headYaw(LocalPlayer player) {
        if (sent == null) {
            return player.getYRot();
        }
        return headFollowed() ? sent.yRot() : headBefore;
    }

    /**
     * Sends a "use item" packet whose acknowledgement tells when the server has ticked the player since, for a
     * hand whose use does nothing: an empty one, or one holding a plain block. Returns its sequence number, or -1
     * if neither hand would be safe to use.
     */
    private static int sendProbe(ClientPacketListener connection, LocalPlayer player, Look look) {
        InteractionHand hand = null;
        for (InteractionHand candidate : new InteractionHand[] {InteractionHand.OFF_HAND, InteractionHand.MAIN_HAND}) {
            if (doesNothingWhenUsed(player.getItemInHand(candidate))) {
                hand = candidate;
                break;
            }
        }
        if (hand == null || !(player.level() instanceof ClientLevel level)) {
            return -1;
        }
        if (level != probeLevel.get()) {
            // Each level numbers from the start again (a new dimension, a new world).
            probeLevel = new WeakReference<>(level);
            acknowledged = -1;
        }
        // Numbered the way vanilla numbers its own clicks, so the server acknowledges it in order with them.
        try (BlockStatePredictionHandler prediction = ((ClientLevelAccessor) level).placementplus$predictions().startPredicting()) {
            int sequence = prediction.currentSequence();
            connection.send(new ServerboundUseItemPacket(hand, sequence, look.yRot(), look.xRot()));
            return sequence;
        }
    }

    /**
     * Whether using {@code stack} in the air does nothing on the server. Nothing in hand, or a block whose item
     * keeps vanilla's use: that only acts on food and drink, wearables, shields and spears, and passes otherwise.
     * Blocks with their own use (lily pads...) are left out.
     */
    private static boolean doesNothingWhenUsed(ItemStack stack) {
        return stack.isEmpty()
                || stack.getItem().getClass() == BlockItem.class
                && !stack.has(DataComponents.CONSUMABLE)
                && !stack.has(DataComponents.EQUIPPABLE)
                && !stack.has(DataComponents.BLOCKS_ATTACKS)
                && !stack.has(DataComponents.KINETIC_WEAPON);
    }
}
