package net.ptcrys.topodemo.api.pipe.common;

import net.ptcrys.topo.api.api.lang.LangKey;
import net.ptcrys.topo.api.pipe.PipeDistributionStrategy;

import java.util.Objects;

/** Shared name/desc/short LangKey binding for builtin strategies. */
public abstract class LangBoundStrategy implements PipeDistributionStrategy {

    private LangKey nameLang;
    private LangKey descriptionLang;
    private LangKey shortNameLang;

    public final void bindLang(LangKey nameLang, LangKey descriptionLang, LangKey shortNameLang) {
        this.nameLang = Objects.requireNonNull(nameLang, "nameLang");
        this.descriptionLang = Objects.requireNonNull(descriptionLang, "descriptionLang");
        this.shortNameLang = Objects.requireNonNull(shortNameLang, "shortNameLang");
    }

    @Override
    public final LangKey nameLang() {
        return Objects.requireNonNull(nameLang, "strategy lang not bound: " + id());
    }

    @Override
    public final LangKey descriptionLang() {
        return Objects.requireNonNull(descriptionLang, "strategy lang not bound: " + id());
    }

    @Override
    public final LangKey shortNameLang() {
        return Objects.requireNonNull(shortNameLang, "strategy lang not bound: " + id());
    }
}
