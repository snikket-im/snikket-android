package eu.siacs.conversations.utils.zlib;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.NoSuchAlgorithmException;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * <p>
 * Android 2.2 includes Java7 FLUSH_SYNC option, which will be used by this
 * Implementation, preferable via reflection. The @hide was remove in API level
 * 19. This class might thus go away in the future.
 * </p>
 * <p>
 * Please use {@link ZLibOutputStream#SUPPORTED} to check for flush
 * compatibility.
 * </p>
 */
public class ZLibOutputStream extends DeflaterOutputStream {

	/**
	 * The reflection based flush method.
	 */

	private final static Method method;
	/**
	 * SUPPORTED is true if a flush compatible method exists.
	 */
	public final static boolean SUPPORTED;

	/**
	 * Static block to initialize {@link #SUPPORTED} and {@link #method}.
	 */
	static {
		Method m = null;
		try {
			m = Deflater.class.getMethod("deflate", byte[].class, int.class,
					int.class, int.class);
		} catch (SecurityException e) {
		} catch (NoSuchMethodException e) {
		}
		method = m;
		SUPPORTED = (method != null);
	}

	/**
	 * Create a new ZLib compatible output stream wrapping the given low level
	 * stream. ZLib compatiblity means we will send a zlib header.
	 * 
	 * @param os
	 *            OutputStream The underlying stream.
	 * @throws IOException
	 *             In case of a lowlevel transfer problem.
	 * @throws NoSuchAlgorithmException
	 *             In case of a {@link Deflater} error.
	 */
	public ZLibOutputStream(OutputStream os) throws IOException,
			NoSuchAlgorithmException {
		super(os, new Deflater(Deflater.BEST_COMPRESSION));
	}

	/**
	 * Flush the given stream, preferring Java7 FLUSH_SYNC if available.
	 * 
	 * @throws IOException
	 *             In case of a lowlevel exception.
	 */
	@Override
	public void flush() throws IOException {
		if (!SUPPORTED) {
			super.flush();
			return;
		}
		try {
			int count = 0;
			do {
				count = (Integer) method.invoke(def, buf, 0, buf.length, 3);
				if (count > 0) {
					out.write(buf, 0, count);
				}
			} while (count > 0);
		} catch (IllegalArgumentException e) {
			throw new IOException("Can't flush");
		} catch (IllegalAccessException e) {
			throw new IOException("Can't flush");
		} catch (InvocationTargetException e) {
			throw new IOException("Can't flush");
		}
		super.flush();
	}

}
