package org.coralprotocol.coralserver.template

import org.coralprotocol.coralserver.template.templates.NetworkServiceTemplate

class SessionTemplateRegistry {
    private val templates: Map<String, SessionTemplate> = mapOf(
        NetworkServiceTemplate.info.slug to NetworkServiceTemplate,
    )

    fun list(): List<SessionTemplateInfo> = templates.values.map { it.info }

    fun get(slug: String): SessionTemplate? = templates[slug]
}
