/*
 * SuperLooperSnooper
 * Copyright (C) 2021 Matt Baxter
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
package org.kitteh.superloopersnooper;

import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;
import org.bukkit.plugin.SimplePluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.jgrapht.alg.cycle.JohnsonSimpleCycles;
import org.jgrapht.graph.guava.MutableGraphAdapter;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

public final class SuperLooperSnooper extends JavaPlugin {
    private List<List<String>> cycles;
    private Exception exception;

    @Override
    public void onLoad() {
        try {
            this.cycles = this.whee();
        } catch (Exception e) {
            this.exception = e;
        }
    }

    @SuppressWarnings({"UnstableApiUsage", "unchecked"})
    private List<List<String>> whee() throws Exception {
        // Get original graph.
        // We need to convert to a newer guava, so this is all done via reflection
        //  and the newer guava is relocated
        Field graphField = SimplePluginManager.class.getDeclaredField("dependencyGraph");
        graphField.setAccessible(true);
        Object originalGraph = graphField.get(this.getServer().getPluginManager());

        // Get nodes
        Method methodNodes = originalGraph.getClass().getMethod("nodes");
        methodNodes.setAccessible(true);
        Set<String> nodes = (Set<String>) methodNodes.invoke(originalGraph);

        // Successors method
        Method methodSuccessors = originalGraph.getClass().getMethod("successors", Object.class);
        methodSuccessors.setAccessible(true);

        // New graph
        MutableGraph<String> graph = GraphBuilder.directed().build();

        // New edges!
        for (String node : nodes) {
            Set<String> successors = (Set<String>) methodSuccessors.invoke(originalGraph, node);
            for (String successor : successors) {
                graph.putEdge(node, successor);
            }
        }

        return new JohnsonSimpleCycles<>(new MutableGraphAdapter<>(graph)).findSimpleCycles();
    }

    @Override
    public void onEnable() {
        new BukkitRunnable() {
            @Override
            public void run() {
                boolean ex = SuperLooperSnooper.this.exception != null;
                boolean empty = SuperLooperSnooper.this.cycles == null || SuperLooperSnooper.this.cycles.isEmpty();
                Level level = (empty && !ex) ? Level.INFO : Level.SEVERE;
                SuperLooperSnooper.this.getLogger().log(level, "");
                SuperLooperSnooper.this.getLogger().log(level, "I'm here to snoop for loops!");
                SuperLooperSnooper.this.getLogger().log(level, "");
                if (ex) {
                    SuperLooperSnooper.this.getLogger().log(level, " Unfortunately, I instead encountered an error!\n", SuperLooperSnooper.this.exception);
                    return;
                }
                if (empty) {
                    SuperLooperSnooper.this.getLogger().log(level, "Found no dependency loops! Yay!");
                    return;
                }
                SuperLooperSnooper.this.getLogger().log(level, " Found dependency loops:");
                for (List<String> list : SuperLooperSnooper.this.cycles) {
                    SuperLooperSnooper.this.getLogger().log(level, "Loop between the following:  " + String.join(" -> ", list) + " -> ...");
                }
                SuperLooperSnooper.this.getLogger().log(level, "");
            }
        }.runTaskLater(this, 40L);
    }
}
