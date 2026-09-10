import com.watabou.glwrap.Matrix;

/** Off-device check of the camera maths: the fork cannot be run on this
 *  machine, so the projection and view composition are verified here. */
public class MatrixTest {
	static int fails = 0;

	static float[] project(float[] mvp, float x, float y, float z) {
		float[] o = new float[4];
		for (int r = 0; r < 4; r++) {
			o[r] = mvp[r] * x + mvp[4 + r] * y + mvp[8 + r] * z + mvp[12 + r];
		}
		return o;
	}

	/** Mirrors Camera3D.updateMatrix() exactly. */
	static float[] cameraMatrix(float eyeX, float eyeY, float eyeZ,
			float yaw, float pitch, float fov, float aspect) {
		float[] proj = new float[16], view = new float[16], out = new float[16];
		Matrix.perspective(proj, fov, aspect, 0.05f, 200f);
		Matrix.setIdentity(view);
		Matrix.rotate3(view, -pitch, 1, 0, 0);
		Matrix.rotate3(view, -yaw, 0, 1, 0);
		Matrix.translate3(view, -eyeX, -eyeY, -eyeZ);
		Matrix.multiply(proj, view, out);
		return out;
	}

	static void check(String what, boolean ok, String detail) {
		System.out.printf("%-52s %s%s%n", what, ok ? "ok" : "FAIL", ok ? "" : "  " + detail);
		if (!ok) fails++;
	}

	public static void main(String[] a) {
		// identity times identity is identity
		float[] i1 = new float[16], i2 = new float[16], r = new float[16];
		Matrix.setIdentity(i1); Matrix.setIdentity(i2);
		Matrix.multiply(i1, i2, r);
		boolean idOk = true;
		for (int i = 0; i < 16; i++) if (Math.abs(r[i] - i1[i]) > 1e-6) idOk = false;
		check("multiply(I, I) == I", idOk, "");

		// looking down -Z, a point straight ahead lands dead centre
		float[] m = cameraMatrix(0, 0, 0, 0, 0, 90f, 1f);
		float[] p = project(m, 0, 0, -10);
		check("point ahead projects to screen centre",
				Math.abs(p[0]) < 1e-4 && Math.abs(p[1]) < 1e-4 && Math.abs(p[3] - 10f) < 1e-4,
				"got x=" + p[0] + " y=" + p[1] + " w=" + p[3]);

		// +X is to the right, +Y is up
		p = project(m, 5, 0, -10);
		check("+X projects right of centre", p[0] > 0, "x=" + p[0]);
		p = project(m, 0, 5, -10);
		check("+Y projects above centre", p[1] > 0, "y=" + p[1]);

		// a point behind the camera has negative w (it is clipped away)
		p = project(m, 0, 0, 10);
		check("point behind camera has w < 0", p[3] < 0, "w=" + p[3]);

		// yaw 90 turns to face -X, so a point due west lands dead centre
		m = cameraMatrix(0, 0, 0, 90f, 0, 90f, 1f);
		p = project(m, -10, 0, 0);
		check("yaw=90 faces -X (west)",
				Math.abs(p[0]) < 1e-3 && Math.abs(p[3] - 10f) < 1e-3,
				"x=" + p[0] + " w=" + p[3]);

		// the eye position actually moves the world
		m = cameraMatrix(30, 0, 45, 0, 0, 90f, 1f);
		p = project(m, 30, 0, 35);
		check("eye offset keeps the tile ahead centred",
				Math.abs(p[0]) < 1e-3 && Math.abs(p[3] - 10f) < 1e-3,
				"x=" + p[0] + " w=" + p[3]);

		// pitch up puts a level point below centre
		m = cameraMatrix(0, 0, 0, 0, 30f, 90f, 1f);
		p = project(m, 0, 0, -10);
		check("pitch=+30 pushes a level point below centre", p[1] < 0, "y=" + p[1]);

		// wider aspect squeezes x, never y
		float[] wide = cameraMatrix(0, 0, 0, 0, 0, 90f, 2f);
		float[] pw = project(wide, 5, 5, -10);
		float[] ps = project(cameraMatrix(0, 0, 0, 0, 0, 90f, 1f), 5, 5, -10);
		check("aspect 2:1 narrows x but leaves y",
				pw[0] < ps[0] && Math.abs(pw[1] - ps[1]) < 1e-4,
				"xw=" + pw[0] + " xs=" + ps[0]);

		System.out.println(fails == 0 ? "=== MATRIXTEST PASSED" : "=== MATRIXTEST FAILED (" + fails + ")");
		System.exit(fails == 0 ? 0 : 1);
	}
}
