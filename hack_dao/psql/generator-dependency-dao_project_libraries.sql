drop table if exists public.project_libraries;

set search_path to public;

create table project_libraries (
  id                          text primary key check(util.non_empty_trimmed_string(id)),
  organization_id             text not null check(util.organization_id(organization_id)),
  project_id                  text not null check(util.non_empty_trimmed_string(project_id)),
  group_id                    text not null check(util.non_empty_trimmed_string(group_id)),
  artifact_id                 text not null check(util.non_empty_trimmed_string(artifact_id)),
  version                     text not null check(util.non_empty_trimmed_string(version)),
  cross_build_version         text check(util.null_or_non_empty_trimmed_string(cross_build_version)),
  path                        text not null check(util.non_empty_trimmed_string(path)),
  library_id                  text check(util.null_or_non_empty_trimmed_string(library_id)),
  created_at                  timestamptz not null default now(),
  updated_at                  timestamptz not null default now(),
  updated_by_user_id          text not null check(util.non_empty_trimmed_string(updated_by_user_id)),
  hash_code                   bigint not null
);

create index project_libraries_organization_id_idx on project_libraries(organization_id);
create index project_libraries_project_id_idx on project_libraries(project_id);
create index project_libraries_artifact_id_idx on project_libraries(artifact_id);
create index project_libraries_group_id_idx on project_libraries(group_id);
create index project_libraries_library_id_idx on project_libraries(library_id);
create index project_libraries_version_idx on project_libraries(version);

select schema_evolution_manager.create_updated_at_trigger('public', 'project_libraries');
