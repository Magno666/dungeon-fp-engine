package android.util;

import java.util.LinkedHashMap;
import java.util.Map;

/** Desktop stand-in for Android's SparseArray, used only so the level
 *  generator can be run off-device. Not shipped in the APK. */
public class SparseArray<E> {
    private final LinkedHashMap<Integer, E> map = new LinkedHashMap<Integer, E>();

    public SparseArray() {}
    public SparseArray(int initialCapacity) {}

    public void put(int key, E value) { map.put(key, value); }
    public E get(int key) { return map.get(key); }
    public E get(int key, E valueIfKeyNotFound) {
        E v = map.get(key); return v == null ? valueIfKeyNotFound : v;
    }
    public void remove(int key) { map.remove(key); }
    public void delete(int key) { map.remove(key); }
    public void clear() { map.clear(); }
    public int size() { return map.size(); }
    public int keyAt(int index) { return keys()[index]; }
    public E valueAt(int index) { return map.get(keyAt(index)); }
    public void setValueAt(int index, E value) { map.put(keyAt(index), value); }
    public int indexOfKey(int key) {
        int[] k = keys();
        for (int i = 0; i < k.length; i++) if (k[i] == key) return i;
        return -1;
    }
    public int indexOfValue(E value) {
        int[] k = keys();
        for (int i = 0; i < k.length; i++) {
            E v = map.get(k[i]);
            if (v == value || (v != null && v.equals(value))) return i;
        }
        return -1;
    }
    public void append(int key, E value) { map.put(key, value); }
    public void removeAt(int index) { map.remove(keyAt(index)); }

    private int[] keys() {
        int[] k = new int[map.size()];
        int i = 0;
        for (Map.Entry<Integer, E> e : map.entrySet()) k[i++] = e.getKey();
        java.util.Arrays.sort(k);
        return k;
    }
}
