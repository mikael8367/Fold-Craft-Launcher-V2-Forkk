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

import com.mio.data.Renderer;
import com.mio.manager.RendererManager;
import com.mio.util.LauncherUtilKt;
import com.tungsten.fcl.R;
import com.tungsten.fcl.setting.GameOption;
import com.tungsten.fcl.util.RuntimeUtils;
import com.tungsten.fclauncher.bridge.FCLBridge;
import com.tungsten.fcl.FCLApp;
import com.tungsten.fclcore.auth.AuthInfo;
import com.tungsten.fclcore.game.GameRepository;
import com.tungsten.fclcore.game.LaunchOptions;
import com.tungsten.fclcore.game.Version;
import com.tungsten.fclcore.launch.DefaultLauncher;
import com.tungsten.fclcore.util.Logging;
import com.tungsten.fclcore.util.io.FileUtils;
import com.tungsten.fclcore.util.versioning.GameVersionNumber;
import com.tungsten.fcllibrary.util.LocaleUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public final class FCLGameLauncher extends DefaultLauncher {

    private static final String[] PERFORMANCE_PROFILE_KEYS = {
            "renderDistance", "fancyGraphics", "graphics", "ao", "ambientOcclusion",
            "particles", "mipmapLevels", "entityShadows", "clouds", "renderClouds",
            "biomeBlendRadius", "entityDistanceScaling", "simulationDistance"
    };

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

    private void generateOptionsTxt() {
        File optionsFile = new File(repository.getRunDirectory(version.getId()), "options.txt");
        File configFolder = new File(repository.getRunDirectory(version.getId()), "config");

        if (!configFolder.isDirectory() && !configFolder.mkdirs() && !configFolder.isDirectory()) {
            Logging.LOG.warning("Unable to create Minecraft config directory: " + configFolder);
        }
        File profileBackup = new File(optionsFile.getParentFile(), "options.txt.fcl-backup");
        SharedPreferences launcherPrefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE);
        boolean fpsBooster = launcherPrefs.getBoolean("fpsBoosterEnabled", false);
        boolean ramSaver = launcherPrefs.getBoolean("ramSaverEnabled", false);
        boolean performanceProfileEnabled = fpsBooster || ramSaver;
        String performanceStateKey = "performanceRamSaverApplied:" + version.getId();
        boolean hasPreviousPerformanceState = launcherPrefs.contains(performanceStateKey);
        boolean previousRamSaver = launcherPrefs.getBoolean(performanceStateKey, false);

        File splashFile = new File(configFolder, "splash.properties");
        try {
            FileUtils.writeText(splashFile, "enabled=false");
        } catch (IOException e) {
            Logging.LOG.log(Level.WARNING, "Unable to disable forge animation", e);
        }
        // 仅对 MC 26.3+ 关闭 FML 早期加载屏：其 ANGLE/Vulkan 渲染在启动期反复分配 gralloc 缓冲，
        // 会与启动器 UI 的 present 在 vendor gralloc 锁上互堵（UI 冻死直至 ANR）。
        // 更早版本无此冲突，须清掉残留的关闭键，保留模组加载进度动画。
        // 键置于文件头部，不会落入任何表作用域；FML 加载后会按 spec 回写补全注释与其余键。
        File fmlToml = new File(configFolder, "fml.toml");
        GameVersionNumber gameVersion = GameVersionNumber.asGameVersion(repository.getGameVersion(version).orElse("0.0"));
        boolean disableEarlyWindow = gameVersion.compareTo("26.3") >= 0;
        if (fmlToml.isFile()) {
            try (BufferedReader bfr = new BufferedReader(new FileReader(fmlToml))) {
                StringBuilder str = new StringBuilder(disableEarlyWindow ? "earlyWindowControl = false\n" : "");
                String line;
                while ((line = bfr.readLine()) != null) {
                    if (!line.strip().startsWith("earlyWindowControl")) {
                        str.append(line).append('\n');
                    }
                }
                try (FileWriter fw = new FileWriter(fmlToml)) {
                    fw.write(str.toString());
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
        GameOption gameOption = null;
        if (!optionsFile.exists()) {
            try {
                RuntimeUtils.copyAssets(context, "options.txt", optionsFile.getAbsolutePath());
                gameOption = new GameOption(optionsFile.getParent());
                gameOption.set("lang", LocaleUtils.isChinese(context) ? "zh_cn" : "en_us");
            } catch (IOException e) {
                Logging.LOG.log(Level.WARNING, "Unable to generate options.txt", e);
            }
        }
        if (gameOption == null)
            gameOption = new GameOption(optionsFile.getParent());
        fixLang(gameOption);
        fixOptions(gameOption);

        Map<String, String> baselineToVerify = null;
        boolean verifyMaxFpsBaseline = false;
        boolean performanceProfileApplied = false;
        boolean restoreMaxFpsDuringProfile = false;
        Map<String, String> profileBaselineForState = null;
        if (performanceProfileEnabled) {
            // Never change the profile-managed options unless a recoverable
            // baseline exists. The backup is taken before any profile is applied.
            boolean backupCreatedThisRun = false;
            if (!profileBackup.isFile()) {
                backupCreatedThisRun = createPerformanceBackup(optionsFile, profileBackup);
                if (!backupCreatedThisRun) {
                    Logging.LOG.warning("Skipping performance profile because its options backup could not be created.");
                }
            }
            if (profileBackup.isFile()) {
                Map<String, String> baseline = readOptionValues(profileBackup);
                if (baseline == null) {
                    Logging.LOG.warning("Skipping performance profile because its options backup could not be read.");
                } else {
                    // The FPS cap is owned only by RAM Saver. Preserve user changes
                    // in FPS-only mode, but undo an older RAM Saver cap when modes
                    // switch or a legacy backup has no recorded profile state.
                    boolean restoreMaxFps = ramSaver || previousRamSaver
                            || (!hasPreviousPerformanceState && !backupCreatedThisRun);
                    restorePerformanceOptions(gameOption, baseline, restoreMaxFps);
                    applyPerformanceProfile(gameOption, gameVersion, fpsBooster, ramSaver);
                    restoreMaxFpsDuringProfile = restoreMaxFps;
                    profileBaselineForState = baseline;
                    performanceProfileApplied = true;
                }
            }
        } else if (profileBackup.isFile()) {
            Map<String, String> baseline = readOptionValues(profileBackup);
            if (baseline != null) {
                boolean restoreMaxFps = previousRamSaver || !hasPreviousPerformanceState;
                restorePerformanceOptions(gameOption, baseline, restoreMaxFps);
                baselineToVerify = baseline;
                verifyMaxFpsBaseline = restoreMaxFps;
            } else {
                Logging.LOG.warning("Unable to read performance profile backup; preserving it for recovery.");
            }
        }
        gameOption.save();
        if (performanceProfileApplied) {
            if (performanceOptionsMatchExpected(optionsFile, profileBaselineForState, gameVersion,
                    fpsBooster, ramSaver, restoreMaxFpsDuringProfile)) {
                launcherPrefs.edit().putBoolean(performanceStateKey, ramSaver).apply();
            } else {
                Logging.LOG.warning("Performance profile write could not be verified; keeping the previous profile state.");
            }
        }
        if (baselineToVerify != null) {
            if (performanceOptionsMatchBaseline(optionsFile, baselineToVerify, verifyMaxFpsBaseline)) {
                if (profileBackup.delete()) {
                    launcherPrefs.edit().remove(performanceStateKey).apply();
                } else {
                    Logging.LOG.warning("Unable to delete performance profile backup: " + profileBackup);
                }
            } else {
                // Keep the baseline so a failed write can be retried next launch.
                Logging.LOG.warning("Performance profile options were not fully restored; keeping backup for retry.");
            }
        }
    }

    private boolean createPerformanceBackup(File optionsFile, File profileBackup) {
        File tempBackup = new File(profileBackup.getParentFile(), profileBackup.getName() + ".tmp");
        try {
            if (!optionsFile.isFile()) {
                Logging.LOG.warning("Cannot back up Minecraft options because options.txt is missing.");
                return false;
            }
            Files.copy(optionsFile.toPath(), tempBackup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(tempBackup.toPath(), profileBackup.toPath(),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tempBackup.toPath(), profileBackup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return profileBackup.isFile();
        } catch (IOException e) {
            if (tempBackup.exists() && !tempBackup.delete()) {
                Logging.LOG.warning("Unable to delete temporary performance backup: " + tempBackup);
            }
            Logging.LOG.log(Level.WARNING, "Unable to back up Minecraft options before performance tuning", e);
            return false;
        }
    }

    private Map<String, String> readOptionValues(File file) {
        if (file == null || !file.isFile()) {
            return null;
        }
        Map<String, String> values = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                int colon = line.indexOf(':');
                if (colon > 0) {
                    values.put(line.substring(0, colon), line.substring(colon + 1));
                }
            }
            if (values.isEmpty()) {
                // An empty or wholly malformed backup cannot prove the original
                // profile-managed values. Keep it for manual recovery and do not
                // apply a profile that we could not safely undo.
                Logging.LOG.warning("Minecraft options backup has no valid entries: " + file);
                return null;
            }
            return values;
        } catch (IOException e) {
            Logging.LOG.log(Level.WARNING, "Unable to read Minecraft options file: " + file, e);
            return null;
        }
    }

    private void restorePerformanceOptions(GameOption gameOption, Map<String, String> baseline,
                                           boolean restoreMaxFps) {
        for (String key : PERFORMANCE_PROFILE_KEYS) {
            if (baseline.containsKey(key)) {
                gameOption.set(key, baseline.get(key));
            } else {
                gameOption.remove(key);
            }
        }
        if (restoreMaxFps) {
            if (baseline.containsKey("maxFps")) {
                gameOption.set("maxFps", baseline.get("maxFps"));
            } else {
                gameOption.remove("maxFps");
            }
        }
    }

    private boolean performanceOptionsMatchExpected(File optionsFile, Map<String, String> baseline,
                                                    GameVersionNumber gameVersion, boolean fpsBooster,
                                                    boolean ramSaver, boolean verifyMaxFps) {
        if (baseline == null) {
            return false;
        }

        Map<String, String> expected = new HashMap<>();
        for (String key : PERFORMANCE_PROFILE_KEYS) {
            if (baseline.containsKey(key)) {
                expected.put(key, baseline.get(key));
            }
        }
        expected.put("renderDistance", String.valueOf(ramSaver ? 4 : (fpsBooster ? 6 : 8)));
        expected.put("fancyGraphics", "false");
        expected.put("graphics", "0");
        expected.put("ao", "0");
        expected.put("ambientOcclusion", "0");
        expected.put("particles", "2");
        expected.put("mipmapLevels", "0");
        expected.put("entityShadows", "false");
        expected.put("clouds", "false");
        expected.put("renderClouds", "false");
        if (gameVersion.compareTo("1.15") >= 0) {
            expected.put("biomeBlendRadius", "0");
        }
        if (gameVersion.compareTo("1.16") >= 0) {
            expected.put("entityDistanceScaling", ramSaver ? "0.5" : "0.75");
        }
        if (gameVersion.compareTo("1.18") >= 0) {
            expected.put("simulationDistance", ramSaver ? "4" : "6");
        }
        if (ramSaver) {
            expected.put("maxFps", "60");
        } else if (verifyMaxFps && baseline.containsKey("maxFps")) {
            expected.put("maxFps", baseline.get("maxFps"));
        }

        Map<String, String> current = readOptionValues(optionsFile);
        if (current == null) {
            return false;
        }
        for (String key : PERFORMANCE_PROFILE_KEYS) {
            if (expected.containsKey(key)) {
                if (!expected.get(key).equals(current.get(key))) {
                    return false;
                }
            } else if (current.containsKey(key)) {
                return false;
            }
        }
        if (ramSaver || verifyMaxFps) {
            if (expected.containsKey("maxFps")) {
                return expected.get("maxFps").equals(current.get("maxFps"));
            }
            return !current.containsKey("maxFps");
        }
        return true;
    }

    private boolean performanceOptionsMatchBaseline(File optionsFile, Map<String, String> baseline,
                                                    boolean verifyMaxFps) {
        Map<String, String> current = readOptionValues(optionsFile);
        if (current == null) {
            return false;
        }
        for (String key : PERFORMANCE_PROFILE_KEYS) {
            if (baseline.containsKey(key)) {
                if (!baseline.get(key).equals(current.get(key))) {
                    return false;
                }
            } else if (current.containsKey(key)) {
                return false;
            }
        }
        if (verifyMaxFps) {
            if (baseline.containsKey("maxFps")) {
                if (!baseline.get("maxFps").equals(current.get("maxFps"))) {
                    return false;
                }
            } else if (current.containsKey("maxFps")) {
                return false;
            }
        }
        return true;
    }

    /**
     * Applies conservative options supported by most Minecraft generations.
     * The game ignores settings it does not recognize; version-specific keys
     * are only added when that option exists in the corresponding generation.
     */
    private void applyPerformanceProfile(GameOption gameOption, GameVersionNumber gameVersion,
                                         boolean fpsBooster, boolean ramSaver) {
        int renderDistance = ramSaver ? 4 : (fpsBooster ? 6 : 8);
        gameOption.set("renderDistance", String.valueOf(renderDistance));
        gameOption.set("fancyGraphics", "false");
        gameOption.set("graphics", "0");
        gameOption.set("ao", "0");
        gameOption.set("ambientOcclusion", "0");
        gameOption.set("particles", "2");
        gameOption.set("mipmapLevels", "0");
        gameOption.set("entityShadows", "false");
        gameOption.set("clouds", "false");
        gameOption.set("renderClouds", "false");
        // The FPS Booster preserves the player's chosen FPS cap. The RAM saver
        // uses a conservative cap to reduce rendering pressure and heat.
        if (ramSaver) {
            gameOption.set("maxFps", "60");
        }

        if (gameVersion.compareTo("1.15") >= 0) {
            gameOption.set("biomeBlendRadius", "0");
        }
        if (gameVersion.compareTo("1.16") >= 0) {
            gameOption.set("entityDistanceScaling", ramSaver ? "0.5" : "0.75");
        }
        if (gameVersion.compareTo("1.18") >= 0) {
            gameOption.set("simulationDistance", ramSaver ? "4" : "6");
        }
    }

    private void fixOptions(GameOption gameOption) {
        GameVersionNumber gameVersion = GameVersionNumber.asGameVersion(repository.getGameVersion(version).orElse("0.0"));
        gameOption.set("touchscreen", "false");
        if (gameVersion.compareTo("1.13") < 0) {
            gameOption.set("key_key.fullscreen", "0");
            gameOption.set("key_key.streamStartStop", "0");
        }
    }

    private void fixLang(GameOption gameOption) {
        GameVersionNumber gameVersion = GameVersionNumber.asGameVersion(repository.getGameVersion(version).orElse("0.0"));
        if (gameVersion.compareTo("1.1") < 0) {
            return;
        }
        boolean toUpper = gameVersion.compareTo("1.11") < 0;
        String lang;
        lang = gameOption.get("lang");
        if (lang == null) return;
        String[] parts = lang.split("_", 2);
        if (parts.length != 2) return;
        lang = parts[0] + "_" + (toUpper ? parts[1].toUpperCase() : parts[1].toLowerCase());
        gameOption.set("lang", lang);
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
        generateOptionsTxt();
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
