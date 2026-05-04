package com.srk.myutils.yd.core;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Configuration for output file placement and overwrite behaviour.
 *
 * <p>Exactly one of {@code outputPath} or {@code outputDir} may be present.
 * When neither is present, the current working directory is used (AC-3.1).
 *
 * @param outputPath literal output path from {@code --output} (AC-3.5)
 * @param outputDir  output directory from {@code --output-dir} (AC-3.2)
 * @param force      {@code true} to overwrite existing files (AC-3.6)
 */
public record OutputConfig(
        Optional<Path> outputPath,
        Optional<Path> outputDir,
        boolean force
) { }
