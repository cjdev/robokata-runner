package com.cj.robokata;

import java.io.InputStream;
import java.io.OutputStream;

public interface CommandRunner {

	void run(OutputStream sink, InputStream input, String command, String ... args);

	void runPassThrough(String command, String ... args);

	String run(InputStream in, String command, String ... args);

	String run(String command, String ... args);

}
