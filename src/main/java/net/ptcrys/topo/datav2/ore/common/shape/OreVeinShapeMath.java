package net.ptcrys.topo.datav2.ore.common.shape;

/**
 * Containment math for built-in vein shapes. Registered as method references from
 * {@link net.ptcrys.topo.datav2.ore.BuiltinOIOreShapes}; add-ons may reuse or contribute their own.
 */
public final class OreVeinShapeMath {

    private OreVeinShapeMath() {}

    public static boolean sphere(int dx, int dy, int dz, int radius) {
        if (radius <= 0) {
            return false;
        }
        double x = (double) dx / radius;
        double y = (double) dy / radius;
        double z = (double) dz / radius;
        double horizontal = Math.sqrt(x * x + z * z);
        return horizontal * horizontal + y * y <= 1.0;
    }

    public static boolean filledPentagram(int dx, int dy, int dz, int radius) {
        if (radius <= 0) {
            return false;
        }
        double x = (double) dx / radius;
        double y = (double) dy / radius;
        double z = (double) dz / radius;
        return Math.abs(y) <= 0.65 && filledPentagramXz(x, z);
    }

    public static boolean variableWidthRing(int dx, int dy, int dz, int radius) {
        if (radius <= 0) {
            return false;
        }
        double x = (double) dx / radius;
        double y = (double) dy / radius;
        double z = (double) dz / radius;
        double horizontal = Math.sqrt(x * x + z * z);
        double vertical = Math.abs(y);
        double width = 0.16 + 0.18 * (1.0 - vertical);
        return Math.abs(horizontal - 0.62) <= width && vertical <= 0.7;
    }

    public static boolean triangle(int dx, int dy, int dz, int radius) {
        if (radius <= 0) {
            return false;
        }
        double x = (double) dx / radius;
        double y = (double) dy / radius;
        double z = (double) dz / radius;
        if (Math.abs(y) > 0.55) {
            return false;
        }
        double pz = z + 0.18;
        return pz >= -0.72 && pz <= 0.72 - Math.abs(x) * 1.35;
    }

    public static boolean threeStarSystem(int dx, int dy, int dz, int radius) {
        if (radius <= 0) {
            return false;
        }
        double x = (double) dx / radius;
        double y = (double) dy / radius;
        double z = (double) dz / radius;
        return ball(x + 0.42, y, z, 0.48) || ball(x - 0.42, y, z, 0.48) || ball(x, y, z + 0.5, 0.42);
    }

    private static boolean ball(double x, double y, double z, double radius) {
        return x * x + y * y + z * z <= radius * radius;
    }

    private static boolean filledPentagramXz(double x, double z) {
        double[] xs = new double[10];
        double[] zs = new double[10];
        for (int i = 0; i < 10; i++) {
            double pointRadius = (i & 1) == 0 ? 1.0 : 0.42;
            double angle = i * Math.PI / 5.0;
            xs[i] = Math.cos(angle) * pointRadius;
            zs[i] = Math.sin(angle) * pointRadius;
        }
        return containsPolygon(xs, zs, x, z);
    }

    private static boolean containsPolygon(double[] polygonX, double[] polygonZ, double x, double z) {
        boolean inside = false;
        for (int i = 0, j = polygonX.length - 1; i < polygonX.length; j = i++) {
            boolean crosses = (polygonZ[i] > z) != (polygonZ[j] > z);
            if (crosses) {
                double edgeX = (polygonX[j] - polygonX[i]) * (z - polygonZ[i]) / (polygonZ[j] - polygonZ[i]) + polygonX[i];
                if (x < edgeX) {
                    inside = !inside;
                }
            }
        }
        return inside;
    }
}
