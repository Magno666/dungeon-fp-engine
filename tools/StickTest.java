import com.github.dachhack.sprout.FirstPersonControls;
import com.github.dachhack.sprout.FirstPerson;

/** The virtual stick, ported from the Godot spike. The hysteresis case is
 *  a regression test: without it a thumb near 45 degrees fires spurious
 *  steps, which is exactly what the spike caught on a real phone. */
public class StickTest {
	static int fails = 0;
	static void check(String what, boolean ok, String detail) {
		System.out.printf("%-58s %s%s%n", what, ok ? "ok" : "FAIL", ok ? "" : "  " + detail);
		if (!ok) fails++;
	}
	static int dir(float x, float y, int held) {
		return FirstPersonControls.stickDirection(x, y, held);
	}

	public static void main(String[] a) {
		float dz = FirstPersonControls.deadZonePx;

		// Inside the dead zone nothing registers, so a resting thumb is still.
		check("a thumb inside the dead zone means no direction",
				dir(dz * 0.5f, 0, -1) == -1 && dir(0, dz * 0.5f, -1) == -1, "");

		// Eight sectors now, numbered clockwise from forward: 0 ahead,
		// 1 ahead-right, 2 right, 3 back-right, 4 back, 5 back-left,
		// 6 left, 7 ahead-left. Pixel Dungeon moves on NEIGHBOURS8 and the
		// old four-way stick was silently removing diagonal movement.
		check("push up is forward",    dir(0, -dz * 3, -1) == 0, "" + dir(0, -dz*3, -1));
		check("push right is right",   dir(dz * 3, 0, -1) == 2, "" + dir(dz*3, 0, -1));
		check("push down is back",     dir(0, dz * 3, -1) == 4, "" + dir(0, dz*3, -1));
		check("push left is left",     dir(-dz * 3, 0, -1) == 6, "" + dir(-dz*3, 0, -1));

		// The diagonals are real headings, not rounding errors.
		check("up-right is the forward diagonal",
				dir(dz * 3, -dz * 3, -1) == 1, "" + dir(dz*3, -dz*3, -1));
		check("down-left is the back diagonal",
				dir(-dz * 3, dz * 3, -1) == 5, "" + dir(-dz*3, dz*3, -1));

		// A sector is 45 degrees wide, so its centre is what reads cleanly.
		// 30 degrees off forward is genuinely the diagonal now.
		check("a push 37 degrees off forward is the diagonal",
				dir(30, -40, -1) == 1, "" + dir(30, -40, -1));

		// The one that matters: resting on a boundary must not chatter
		// between two neighbouring headings, or every wobble costs a turn.
		int held = dir(0, -dz * 3, -1);
		check("a straight forward push reads as forward", held == 0, "" + held);
		check("drifting into the neighbouring sector keeps the held one",
				dir(dz * 3, -dz * 3, held) == 0,
				"flipped to " + dir(dz*3, -dz*3, held));

		// But a move two sectors across is deliberate and answers at once.
		check("a decisive sideways push does switch",
				dir(dz * 3, 0, held) == 2, "" + dir(dz*3, 0, held));

		// A straight reversal needs no margin either: it is four sectors
		// away, and crossing the dead zone to get there is already intent.
		check("reversing from forward to back switches",
				dir(0, dz * 3, 0) == 4, "" + dir(0, dz*3, 0));

		// Releasing past the dead zone clears the direction.
		check("returning inside the dead zone clears it",
				dir(2, 2, 0) == -1, "" + dir(2, 2, 0));

		// facing8() snaps the free look to one of eight ways.
		FirstPerson.yaw = 0f;   check("yaw 0 faces north (8-way)",
				FirstPerson.facing8() == 0, "" + FirstPerson.facing8());
		FirstPerson.yaw = -45f; check("yaw -45 faces north-east",
				FirstPerson.facing8() == 1, "" + FirstPerson.facing8());
		FirstPerson.yaw = -90f; check("yaw -90 faces east (8-way)",
				FirstPerson.facing8() == 2, "" + FirstPerson.facing8());

		// facing() must snap the free look to one of four ways.
		FirstPerson.yaw = 0f;    check("yaw 0 faces north",   FirstPerson.facing() == 0, "" + FirstPerson.facing());
		FirstPerson.yaw = -95f;  check("yaw -95 faces east",  FirstPerson.facing() == 1, "" + FirstPerson.facing());
		FirstPerson.yaw = 170f;  check("yaw 170 faces south", FirstPerson.facing() == 2, "" + FirstPerson.facing());
		FirstPerson.yaw = 88f;   check("yaw 88 faces west",   FirstPerson.facing() == 3, "" + FirstPerson.facing());
		FirstPerson.yaw = 44f;   check("yaw 44 still faces north", FirstPerson.facing() == 0, "" + FirstPerson.facing());
		FirstPerson.yaw = 0f;

		System.out.println(fails == 0 ? "=== STICKTEST PASSED" : "=== STICKTEST FAILED (" + fails + ")");
		System.exit(fails == 0 ? 0 : 1);
	}
}
