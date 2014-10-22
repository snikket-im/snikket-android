package eu.siacs.conversations.utils.zlib;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/**
 * ZLibInputStream is a zlib and input stream compatible version of an
 * InflaterInputStream. This class solves the incompatibility between
 * {@link InputStream#available()} and {@link InflaterInputStream#available()}.
 */
public class ZLibInputStream extends InflaterInputStream {

	/**
	 * Construct a ZLibInputStream, reading data from the underlying stream.
	 * 
	 * @param is
	 *            The {@code InputStream} to read data from.
	 * @throws IOException
	 *             If an {@code IOException} occurs.
	 */
	public ZLibInputStream(InputStream is) throws IOException {
		super(is, new Inflater(), 512);
	}

	/**
	 * Provide a more InputStream compatible version of available. A return
	 * value of 1 means that it is likly to read one byte without blocking, 0
	 * means that the system is known to block for more input.
	 * 
	 * @return 0 if no data is available, 1 otherwise
	 * @throws IOException
	 */
	@Override
	public int available() throws IOException {
		/*
		 * This is one of the funny code blocks. InflaterInputStream.available
		 * violates the contract of InputStream.available, which breaks kXML2.
		 * 
		 * I'm not sure who's to blame, oracle/sun for a broken api or the
		 * google guys for mixing a sun bug with a xml reader that can't handle
		 * it....
		 * 
		 * Anyway, this simple if breaks suns distorted reality, but helps to
		 * use the api as intended.
		 */
		if (inf.needsInput()) {
			return 0;
		}
		return super.available();
	}

}
