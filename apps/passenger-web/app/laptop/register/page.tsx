import { PassengerIdentityExperience } from "../../../components/identity-experience";

export const metadata = { title: "Create account — Laptop passenger" };

export default function LaptopRegisterPage() {
  return <PassengerIdentityExperience device="laptop" mode="register" />;
}
