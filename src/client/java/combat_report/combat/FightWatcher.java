package combat_report.combat;

import combat_report.record.ReportData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/**
 * The state machine that decides what counts as a fight, and feeds everything else.
 *
 * <p><b>A recording never stops on its own.</b> It runs from the moment you press
 * record until you press stop (or leave the server, which saves rather than
 * discards). What this class does is mark which stretches of that one continuous
 * recording were combat, so the idle stretches can be left out of the numbers - a
 * figure like combo frequency is meaningless if the denominator includes three
 * minutes spent walking back and picking your gear up.
 *
 * <p>Nothing is dropped at capture time because of the fight state. Events are
 * recorded as they happen and the idle ones are removed when the report is
 * summarised, which is the only order that works: a jump thrown a fraction of a
 * second before the opening hit of a fight is a reset attempt, and at the instant
 * it happens no fight has started yet.
 *
 * <p>A fight opens on the first combat event against a player - a swing thrown at
 * them, or a hit taken from them - and closes {@link Constants#FIGHT_IDLE_MS} after
 * the last one, or when they die, leave, or get further away than
 * {@link Constants#FIGHT_MAX_DISTANCE}. It is closed <i>at the last combat event</i>,
 * not at the moment the timer expires, so a fight does not gain eight seconds of
 * dead air on the way out.
 *
 * <p>Misses open a fight as well as hits. Otherwise a duel that starts with three
 * whiffs and then a hit would record only the hit, and the accuracy figure would
 * quietly become 100%.
 */
public final class FightWatcher {

	private static final FightWatcher INSTANCE = new FightWatcher();

	public static FightWatcher get() {
		return INSTANCE;
	}

	private FightWatcher() {
	}

	private ReportData data;

	private final HealthWatch health = new HealthWatch();
	private final VelocityWatch velocity = new VelocityWatch();
	private ComboWatch combosDealt;
	private ComboWatch combosTaken;
	private JumpWatch jumps;
	private TradeWatch trades;

	private boolean inFight;
	private long fightStartMs;
	private long lastCombatMs;
	private UUID opponentId;

	// The swing currently being thrown. Opened at the head of Minecraft.startAttack
	// and committed at its return, so the landed/missed outcome and the hit type -
	// both of which are decided in between - land on the same record.
	private boolean swingOpen;
	private ReportData.Swing swing;
	private Player swingTarget;

	private long pingSum;
	private int pingSamples;
	private int tickCounter;

	public boolean isRecording() {
		return this.data != null;
	}

	public boolean inFight() {
		return this.inFight;
	}

	// ---- lifecycle ----------------------------------------------------------

	public void start(ReportData target) {
		this.data = target;
		this.health.reset();
		this.velocity.reset();
		this.combosDealt = new ComboWatch(c -> this.data.combosDealt.add(c));
		this.combosTaken = new ComboWatch(c -> this.data.combosTaken.add(c));
		this.jumps = new JumpWatch(j -> this.data.jumps.add(j));
		this.trades = new TradeWatch(t -> this.data.trades.add(t), this.health, this.velocity);
		this.inFight = false;
		this.opponentId = null;
		this.swingOpen = false;
		this.swingTarget = null;
		this.pingSum = 0L;
		this.pingSamples = 0;
		this.tickCounter = 0;
	}

	/** Closes everything still open and hands back the finished data. */
	public ReportData stop(Minecraft mc) {
		if (this.data == null) {
			return null;
		}

		endFight(mc);
		this.combosDealt.flush();
		this.combosTaken.flush();
		this.jumps.flush();
		this.trades.flush(mc);

		ComboWatch.sortByStart(this.data.combosDealt);
		ComboWatch.sortByStart(this.data.combosTaken);

		this.data.opponentHealthSeen = this.health.opponentHealthSeen();
		this.data.pingMs = this.pingSamples > 0 ? (double) this.pingSum / this.pingSamples : 0.0;

		ReportData finished = this.data;
		this.data = null;
		return finished;
	}

	// ---- events -------------------------------------------------------------

	/** Minecraft.startAttack, at the head: a left-click attack is being thrown. */
	public void onSwingBegin(Minecraft mc) {
		if (!isRecording()) {
			return;
		}

		LocalPlayer self = mc.player;

		if (self == null || mc.hitResult == null) {
			return;
		}

		// Vanilla's own guards, re-applied because this runs before them. A click
		// during post-whiff cooldown or while eating is not a swing, and letting one
		// through would put a phantom miss in the accuracy figure.
		if (mc.missTime > 0 || self.isHandsBusy()) {
			return;
		}

		ItemStack held = self.getItemInHand(InteractionHand.MAIN_HAND);

		// The other two guards startAttack applies before it swings anything. The
		// second one is not exotic in 1.21.11: every spear carries
		// MINIMUM_ATTACK_CHARGE = 1.0, so cannotAttackWithItem discards every click
		// thrown before the cooldown is full. Without this, spam-clicking a spear
		// filled the report with misses vanilla never threw.
		if (mc.level == null || !held.isItemEnabled(mc.level.enabledFeatures())) {
			return;
		}

		if (self.cannotAttackWithItem(held, 0)) {
			return;
		}

		// Piercing weapons - the 1.21.11 spears - are counted and then left alone.
		// startAttack routes them to MultiPlayerGameMode.piercingAttack, which sends a
		// STAB action and lets the server decide what was hit, so this client is never
		// told whether the stab landed. Recording them as misses was simply wrong, and
		// inferring a landed stab from the opponent losing health would mix a weaker
		// kind of evidence into the accuracy figure. A spear also carries its own
		// ATTACK_RANGE reaching 6.5 blocks, so folding its hits into a range figure
		// built around vanilla's 3.0 would corrupt the one number it is there to give.
		// The count is reported so the exclusion is visible rather than silent.
		if (held.has(DataComponents.PIERCING_WEAPON)) {
			this.data.piercingSwings++;
			return;
		}

		SwingCapture.Aim aim = SwingCapture.resolve(mc, self);

		if (aim == null) {
			return;
		}

		this.swing = new ReportData.Swing();
		this.swing.t = System.currentTimeMillis();
		this.swing.reach = aim.reach();
		this.swing.charge = HitClassifier.charge(self);
		this.swingTarget = aim.target();
		this.swingOpen = true;
	}

	/**
	 * MultiPlayerGameMode.attack, at the head: the swing connected.
	 *
	 * <p>This is the last instant the hit can be classified. The very next thing
	 * vanilla does after {@code player.attack(entity)} is reset the attack-strength
	 * ticker, and the charge is what tells a pick hit from a charged one.
	 */
	public void onLandedHit(Player self, Entity target) {
		if (!isRecording() || !(target instanceof Player opponent)) {
			return;
		}

		long now = System.currentTimeMillis();
		noteCombat(opponent, now, true);
		int index = opponentIndex(opponent);

		if (this.swingOpen) {
			this.swing.landed = true;
			this.swing.type = HitClassifier.classify(self, target).name();
		}

		this.combosDealt.onHit(index, now);
		this.trades.onHitDealt(opponent.getUUID(), index, now);
	}

	/** Minecraft.startAttack, at every return: the swing is over, record it. */
	public void onSwingCommit() {
		if (!this.swingOpen) {
			return;
		}

		this.swingOpen = false;

		if (this.data == null) {
			this.swingTarget = null;
			return;
		}

		if (this.swingTarget != null) {
			noteCombat(this.swingTarget, this.swing.t, this.swing.landed);
			this.swing.opponent = opponentIndex(this.swingTarget);
		}

		this.data.swings.add(this.swing);
		this.swingTarget = null;
	}

	/** LivingEntity.handleDamageEvent: someone took a hit. Only ours matters here. */
	public void onDamage(LivingEntity victim, DamageSource source) {
		if (!isRecording()) {
			return;
		}

		Minecraft mc = Minecraft.getInstance();

		if (mc.player == null || victim != mc.player) {
			return;
		}

		// The damage source arrives with its cause resolved against the client's own
		// level, so the attacker is a real entity here, not an id to guess at.
		if (!(source.getEntity() instanceof Player attacker) || attacker == mc.player) {
			return;
		}

		long now = System.currentTimeMillis();
		noteCombat(attacker, now, true);

		this.combosTaken.onHit(opponentIndex(attacker), now);
		this.jumps.onHitTaken(now);
		this.trades.onHitTaken(attacker.getUUID(), now);
	}

	/**
	 * LivingEntity.jumpFromGround: a real jump, not upward velocity from knockback.
	 *
	 * <p>Captured whether or not combat is active. Whether it counts is decided when
	 * the report is summarised, because it cannot be decided here: a jump thrown a
	 * fraction of a second before the opening hit of a fight is a reset attempt, and
	 * at the moment it happens no fight has started yet.
	 */
	public void onJump(LivingEntity who) {
		if (!isRecording()) {
			return;
		}

		if (who != Minecraft.getInstance().player) {
			return;
		}

		this.jumps.onJump(System.currentTimeMillis(), this.inFight);
	}

	// ---- per-tick -----------------------------------------------------------

	public void tick(Minecraft mc) {
		if (!isRecording()) {
			return;
		}

		long now = System.currentTimeMillis();
		LocalPlayer self = mc.player;

		this.health.tick(mc, now);

		if (self != null) {
			this.velocity.tick(self);
			noteIdentity(self);
		}

		if (this.inFight && shouldEndFight(mc, now)) {
			endFight(mc);
		}

		this.combosDealt.tick(now);
		this.combosTaken.tick(now);

		if (self != null) {
			this.jumps.tick(now, self.onGround());
		}

		this.trades.tick(mc, now);

		// Ping is context on the report and adjusts nothing, so once a second is
		// plenty, and averaging beats sampling whenever the player happened to stop.
		if (++this.tickCounter >= 20) {
			this.tickCounter = 0;
			samplePing(mc);
		}
	}

	// ---- fight segmentation -------------------------------------------------

	/**
	 * Records a combat event, opening a fight if one is not already running.
	 *
	 * @param authoritative whether this event should also make {@code opponent} the
	 *                      player the fight is tracked against. True for a landed
	 *                      hit or a hit taken; false for a miss.
	 *                      <p>A miss keeps the fight alive but does not retarget it.
	 *                      Otherwise one stray whiff at someone walking past would
	 *                      move the fight onto them, and the moment they wandered off
	 *                      the distance check would end a duel that was still going -
	 *                      splitting it in two, inflating the fight count and cutting
	 *                      a combo in half.
	 */
	private void noteCombat(Player opponent, long nowMs, boolean authoritative) {
		if (!this.inFight) {
			this.inFight = true;
			this.fightStartMs = nowMs;
			this.data.fights++;
			this.opponentId = opponent.getUUID();
		} else if (authoritative) {
			this.opponentId = opponent.getUUID();
		}

		this.lastCombatMs = nowMs;
		opponentIndex(opponent);
	}

	private boolean shouldEndFight(Minecraft mc, long nowMs) {
		if (nowMs - this.lastCombatMs > Constants.FIGHT_IDLE_MS) {
			return true;
		}

		Player opponent = findOpponent(mc);

		if (opponent == null || !opponent.isAlive()) {
			return true;
		}

		return mc.player != null
				&& opponent.distanceToSqr(mc.player) > Constants.FIGHT_MAX_DISTANCE * Constants.FIGHT_MAX_DISTANCE;
	}

	private void endFight(Minecraft mc) {
		if (!this.inFight) {
			return;
		}

		// Charged to the last combat event, not to the moment the idle timer expired.
		this.data.fightMs += Math.max(0L, this.lastCombatMs - this.fightStartMs);
		this.inFight = false;
		this.opponentId = null;

		this.combosDealt.flush();
		this.combosTaken.flush();

		// flush, not reset: a trade whose momentum sample was still pending when the
		// fight ended is a trade that happened, and dropping it would quietly shrink
		// the denominator of every figure in that section.
		this.trades.flush(mc);
	}

	private Player findOpponent(Minecraft mc) {
		if (mc.level == null || this.opponentId == null) {
			return null;
		}

		for (Player p : mc.level.players()) {
			if (p.getUUID().equals(this.opponentId)) {
				return p;
			}
		}

		return null;
	}

	// ---- identity -----------------------------------------------------------

	/**
	 * Index of an opponent in the report's name list, adding them if new.
	 *
	 * <p>Formatting codes are stripped: practice servers put colours in display
	 * names, and an unstripped one puts a literal section sign in the report.
	 */
	private int opponentIndex(Player opponent) {
		String name = stripFormatting(opponent.getName().getString());
		int existing = this.data.opponents.indexOf(name);

		if (existing >= 0) {
			return existing;
		}

		this.data.opponents.add(name);
		return this.data.opponents.size() - 1;
	}

	private static String stripFormatting(String raw) {
		StringBuilder out = new StringBuilder(raw.length());

		for (int i = 0; i < raw.length(); i++) {
			char c = raw.charAt(i);

			if (c == SECTION_SIGN && i + 1 < raw.length()) {
				i++;
				continue;
			}

			out.append(c);
		}

		return out.toString().trim();
	}

	private static final char SECTION_SIGN = '§';

	private void noteIdentity(LocalPlayer self) {
		if (this.data.playerName.isEmpty()) {
			this.data.playerName = stripFormatting(self.getName().getString());
			this.data.playerUuid = self.getUUID().toString();
		}
	}

	private void samplePing(Minecraft mc) {
		if (mc.getConnection() == null || mc.player == null) {
			return;
		}

		PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());

		if (info == null) {
			return;
		}

		int latency = info.getLatency();

		if (latency > 0) {
			this.pingSum += latency;
			this.pingSamples++;
		}
	}
}
