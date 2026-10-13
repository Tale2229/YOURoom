package com.example.youroom.domain

/** A room's tag selection is separate from the classroom search conditions. */
internal fun RoomProfile.tagSelection() = SearchFilters(outletsOnly = outlets, tags = allTags)
internal fun RoomProfile.withTagSelection(selection: SearchFilters) =
    copy(outlets = selection.outletsOnly, quiet = false, tags = selection.tags)
internal fun RoomProfile.withPersonalTag(name: String) = if (name == PersonalTags.outlets) copy(outlets = true)
    else copy(quiet = false, tags = allTags + name)
internal fun RoomProfile.withoutPersonalTags() = copy(outlets = false, quiet = false, tags = emptySet())
