-- Clean up existing test entries to avoid duplicate key exceptions on restart
DELETE FROM users WHERE email IN ('admin@omnimove.it', 'traveller@omnimove.it');

-- Insert Admin Profile
-- Plain text password: adminpassword123
INSERT INTO users (name, email, password, role)
VALUES (
           'Omnimove Admin',
           'admin@omnimove.it',
           '$2a$12$CmE.RdujBELdG9wnXkUS3.ECgh4j0sWAr859.oy0MmRLJGNf18p.G',
           'ADMIN'
       );

-- Insert Traveller Profile
-- Plain text password: travellerpassword123
INSERT INTO users (name, email, password, role)
VALUES (
           'John Traveller',
           'traveller@omnimove.it',
           '$2a$12$znNyrIoRNqw8oeSIo8H2QeKIE4SVrTCU7lGlOEZU8MxztRHD1vM1q',
           'TRAVELLER'
       );