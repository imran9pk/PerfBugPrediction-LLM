package org.gradle.api.internal.artifacts.dsl;

import org.gradle.api.artifacts.ModuleIdentifier;

import javax.annotation.Nullable;

public interface ModuleReplacementsData {
    ModuleReplacementsData NO_OP = new ModuleReplacementsData() {
        @Nullable
        @Override
        public Replacement getReplacementFor(ModuleIdentifier sourceModule) {
            return null;
        }

        @Override
        public boolean participatesInReplacements(ModuleIdentifier moduleId) {
            return false;
        }
    };

    @Nullable Replacement getReplacementFor(ModuleIdentifier sourceModule);

    boolean participatesInReplacements(ModuleIdentifier moduleId);

    class Replacement {
        private final ModuleIdentifier target;
        private final String reason;

        Replacement(ModuleIdentifier target, String reason) {
            this.target = target;
            this.reason = reason;
        }

        public ModuleIdentifier getTarget() {
            return target;
        }

        public String getReason() {
            return reason;
        }
    }
}
