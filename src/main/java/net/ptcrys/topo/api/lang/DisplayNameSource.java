package net.ptcrys.topo.api.lang;

/**
 * Subject of a {@link TemplateDisplayName}: supplies the bare en/cn name inserted into {@code %s}.
 * Both languages are required (code-style §3.13).
 */
public interface DisplayNameSource {

    String displayNameEn();

    String displayNameCn();
}
