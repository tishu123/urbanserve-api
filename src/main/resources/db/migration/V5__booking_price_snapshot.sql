-- Existing bookings have no package price snapshot.
alter table booking add column total_amount_paise bigint
    check (total_amount_paise > 0);

-- Store the package details and unit price agreed to at booking time.
create table booking_item (
    booking_id        bigint       not null references booking(id),
    package_code      varchar(80)  not null,
    package_name      varchar(120) not null,
    quantity          integer      not null check (quantity > 0),
    unit_amount_paise bigint       not null check (unit_amount_paise > 0),

    primary key (booking_id, package_code)
);