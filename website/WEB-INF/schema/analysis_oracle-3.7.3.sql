--
-- create table define custom top-level categories for a module 
-- the task must be a baseLsid (no version)
-- the category must be non-null, empty string or any string prefixed with a '.' means
-- the module should be hidden from the top-level categories on the Modules & Pipelines page
create table task_category (
    task varchar(511) not null,
    category varchar(511) not null,
    primary key (task, category)
);
create index idx_tc_task on task_category (task);
create index idx_tc_category on task_category (category);

-- example insert statement
-- insert into task_category(category,task) values
--    ( 'MIT_701X', 'urn:lsid:8086.jtriley.starapp.mit.edu:genepatternmodules:11'  )

commit;

