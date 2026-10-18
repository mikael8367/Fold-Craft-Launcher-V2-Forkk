/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.tungsten.fcl.game;

import android.content.Context;
import android.content.SharedPreferences;

import com.mio.JavaManager;
import com.mio.data.Renderer;
import com.mio.manager.RendererManager;
import com.mio.util.LauncherUtilKt;
import com.tungsten.fcl.R;
import com.tungsten.fclauncher.bridge.FCLBridge;
import com.tungsten.fcl.FCLApp;
import com.tungsten.fclcore.auth.AuthInfo;
import com.tungsten.fclcore.game.GameRepository;
import com.tungsten.fclcore.game.LaunchOptions;
import com.tungsten.fclcore.game.JavaVersion;
import com.tungsten.fclcore.game.Version;
import com.tungsten.fclcore.launch.DefaultLauncher;
import com.tungsten.fclcore.util.Logging;
import com.tungsten.fclcore.util.io.FileUtils;
import com.tungsten.fclcore.util.platform.CommandBuilder;
import com.tungsten.fclcore.util.versioning.GameVersionNumber;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public final class FCLGameLauncher extends DefaultLauncher {

    public FCLGameLauncher(Context context, GameRepository repository, Version version, AuthInfo authInfo, LaunchOptions options) {
        super(context, repository, version, authInfo, options);
    }

    @Override
    protected Map<String, String> getConfigurations() {
        Map<String, String> res = super.getConfigurations();
        res.put("${launcher_name}", LauncherUtilKt.getLauncherName(FCLApp.getAppContext()));
        res.put("${launcher_version}", FCLApp.getAppContext().getString(R.string.app_version));
        return res;
    }

    /**
     * Prepare launcher/loader configuration only. Existing Minecraft options.txt
     * is owned by Minecraft and is deliberately never read or rewritten here.
     */
    private void prepareRuntimeConfig() {
        File configFolder = new File(repository.getRunDirectory(version.getId()), "config");
        if (!configFolder.isDirectory() && !configFolder.mkdirs() && !configFolder.isDirectory()) {
            Logging.LOG.warning("Unable to create Minecraft config directory: " + configFolder);
        }

        File splashFile = new File(configFolder, "splash.properties");
        try {
            FileUtils.writeText(splashFile, "enabled=false");
        } catch (IOException e) {
            Logging.LOG.log(Level.WARNING, "Unable to disable forge animation", e);
        }

        // Only disable the FML early-loading window on versions where its startup
        // renderer conflicts with Android's gralloc synchronization.
        File fmlToml = new File(configFolder, "fml.toml");
        GameVersionNumber gameVersion = GameVersionNumber.asGameVersion(
                repository.getGameVersion(version).orElse("0.0"));
        boolean disableEarlyWindow = gameVersion.compareTo("26.3") >= 0;
        if (fmlToml.isFile()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(fmlToml))) {
                StringBuilder text = new StringBuilder(disableEarlyWindow ? "earlyWindowControl = false\n" : "");
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.strip().startsWith("earlyWindowControl")) {
                        text.append(line).append('\n');
                    }
                }
                try (FileWriter writer = new FileWriter(fmlToml)) {
                    writer.write(text.toString());
                }
            } catch (IOException e) {
                Logging.LOG.log(Level.WARNING, "Unable to write fml.toml", e);
            }
        } else if (disableEarlyWindow) {
            try {
                FileUtils.writeText(fmlToml, "earlyWindowControl = false\n");
            } catch (IOException e) {
                Logging.LOG.log(Level.WARNING, "Unable to write fml.toml", e);
            }
        }
    }

    /**
     * Apply runtime-level performance options without rewriting Minecraft's
     * options.txt. Existing user-specified JVM options take precedence because
     * CommandBuilder adds these as defaults.
     */
    @Override
    protected void appendJvmArgs(CommandBuilder result) {
        SharedPreferences preferences = context.getSharedPreferences("launcher", Context.MODE_PRIVATE);
        boolean fpsBooster = preferences.getBoolean("fpsBoosterEnabled", false)
                || preferences.getBoolean("fpsBoosterUltraEnabled", false);
        boolean ramSaver = preferences.getBoolean("ramSaverEnabled", false);
        if (!fpsBooster && !ramSaver) {
            return;
        }

        JavaVersion javaVersion = options.getJava().isAuto()
                ? JavaManager.getSuitableJavaVersion(version)
                : options.getJava();
        List<String> tuningArguments = PerformanceRuntimeTuning.buildJvmArguments(
                javaVersion.getVersion(), fpsBooster, ramSaver);
        if (!tuningArguments.isEmpty()) {
            result.addAllDefault(tuningArguments);
            Logging.LOG.log(Level.INFO,
                    "Performance tuning requested for Java " + javaVersion.getVersion()
                            + " (FPS Booster=" + fpsBooster
                            + ", RAM Saver=" + ramSaver
                            + "): " + tuningArguments);
        }
    }

    private void modifyIfConfigDetected(String config, String option, String replacement, boolean overwrite, Renderer... renderers) {
        boolean patch = false;
        if (renderers.length == 0) {
            patch = true;
        } else {
            for (Renderer renderer : renderers) {
                if (renderer == options.getRenderer()) {
                    patch = true;
                    break;
                }
            }
        }
        File configFolder = new File(repository.getRunDirectory(version.getId()), "config");
        if (patch && configFolder.exists() && new File(configFolder, config).exists()) {
            File configFile = new File(configFolder, config);
            StringBuilder str = new StringBuilder();
            try (BufferedReader bfr = new BufferedReader(new FileReader(configFile))) {
                String line;
                while ((line = bfr.readLine()) != null) {
                    if (overwrite && line.contains(option)) {
                        str.append(replacement).append("\n");
                    } else {
                        str.append(line).append("\n");
                    }
                }
                if (!overwrite && !str.toString().contains(replacement.replace("false", "").replace("true", ""))) {
                    str.append(replacement);
                }
            } catch (Exception e) {
                Logging.LOG.log(Level.WARNING, "Unable to read " + config + ".", e);
            }
            if (!"".equals(str.toString())) {
                try (FileWriter fw = new FileWriter(configFile)) {
                    fw.write(str.toString());
                } catch (IOException e) {
                    Logging.LOG.log(Level.WARNING, "Unable to write " + config + ".", e);
                }
            }
        }
    }

    private boolean findFiles(File folder, String fileName) {
        File[] fs = folder.listFiles();
        if (fs != null) {
            for (File f : fs) {
                if (f.isDirectory())
                    if (f.listFiles((dir, name) -> name.equals(fileName)) != null)
                        return true;
                if (f.getName().equals(fileName))
                    return true;
            }
        }
        return false;
    }

    @Override
    public FCLBridge launch() throws IOException, InterruptedException {
        prepareRuntimeConfig();
        // Sodium
        modifyIfConfigDetected("sodium-mixins.properties", "", "mixin.features.chunk_rendering=false", false, RendererManager.RENDERER_GL4ES, RendererManager.RENDERER_VGPU);
        // Rubidium
        modifyIfConfigDetected("rubidium-mixins.properties", "", "mixin.features.chunk_rendering=false", false, RendererManager.RENDERER_GL4ES, RendererManager.RENDERER_VGPU);
        // DraconicEvolution
        String config = "brandon3055/DraconicEvolution.cfg";
        modifyIfConfigDetected(config, "B:useShaders=", "B:useShaders=false", true, RendererManager.RENDERER_GL4ES, RendererManager.RENDERER_VGPU, RendererManager.RENDERER_NGGL4ES);
        modifyIfConfigDetected(config, "B:\"crystalShaders\"=", "B:\"crystalShaders\"=false", true, RendererManager.RENDERER_GL4ES, RendererManager.RENDERER_VGPU, RendererManager.RENDERER_NGGL4ES);
        modifyIfConfigDetected(config, "B:\"reactorShaders\"=", "B:\"reactorShaders\"=false", true, RendererManager.RENDERER_GL4ES, RendererManager.RENDERER_VGPU, RendererManager.RENDERER_NGGL4ES);
        modifyIfConfigDetected(config, "B:\"guardianShaders\"=", "B:\"guardianShaders\"=false", true, RendererManager.RENDERER_GL4ES, RendererManager.RENDERER_VGPU, RendererManager.RENDERER_NGGL4ES);
        modifyIfConfigDetected(config, "B:\"otherShaders\"=", "B:\"otherShaders\"=false", true, RendererManager.RENDERER_GL4ES, RendererManager.RENDERER_VGPU, RendererManager.RENDERER_NGGL4ES);
        // Pixelmon
        modifyIfConfigDetected("pixelmon/config.yml", "use-discord-rich-presence:", "use-discord-rich-presence: false", true);
        // ImmersiveEngineering
        modifyIfConfigDetected("immersiveengineering-client.toml", "stencilBufferEnabled", "stencilBufferEnabled = false", true, RendererManager.RENDERER_GL4ES, RendererManager.RENDERER_VGPU, RendererManager.RENDERER_NGGL4ES);
        return super.launch();
    }
}
